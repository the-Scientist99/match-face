import { ActionIcon, Button, Group, Select, Stack, Text, TextInput, Title } from '@mantine/core'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { type FormEvent, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { api, call, type Competition } from '../../api/client'
import { useNotifyError } from '../../api/errors'

/** Teams in the competition, adding (existing or new) and removing them, and generating fixtures. */
export default function ManageTab({ competition }: { competition: Competition }) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const notifyError = useNotifyError()
  const id = competition.id
  const path = { params: { path: { id } } }
  const [teamId, setTeamId] = useState<string | null>(null)
  const [newTeam, setNewTeam] = useState('')
  const teams = useQuery({ queryKey: ['teams', 'mine'], queryFn: () => call(api.GET('/api/teams/mine')) })
  const inIt = new Set(competition.teams.map((ct) => ct.teamId))
  const options = (teams.data ?? []).filter((team) => team.canAdmin && !inIt.has(team.id)).map((team) => ({ value: team.id, label: team.name }))

  const action = useMutation({
    mutationFn: (run: () => Promise<Competition>) => run(),
    onSuccess: (updated) => {
      queryClient.setQueryData(['competition', id], updated)
      void queryClient.invalidateQueries({ queryKey: ['competition', id] })
      void queryClient.invalidateQueries({ queryKey: ['competitions', 'mine'] })
      void queryClient.invalidateQueries({ queryKey: ['matches', 'mine'] })
    },
    onError: notifyError,
  })
  const run = (fn: () => Promise<unknown>) => action.mutate(fn as () => Promise<Competition>)
  const add = (team: string) => run(() => call(api.POST('/api/competitions/{id}/teams', { ...path, body: { teamId: team } })))
  const createAndAdd = async (name: string) => {
    const team = await call(api.POST('/api/teams', { body: { name } }))
    void queryClient.invalidateQueries({ queryKey: ['teams', 'mine'] })
    return call(api.POST('/api/competitions/{id}/teams', { ...path, body: { teamId: team.id } }))
  }
  const generate = () => {
    const hasFixtures = competition.status === 'ACTIVE'
    if (hasFixtures && !window.confirm(t('competition.regenerateWarning'))) return
    run(() => call(api.POST('/api/competitions/{id}/generate', path)))
  }

  return (
    <Stack maw={500}>
      <Title order={5}>{t('competition.teams')}</Title>
      {competition.teams.length ? (
        competition.teams.map((ct) => (
          <Group key={ct.teamId} justify="space-between">
            <Text>{ct.name}</Text>
            <ActionIcon
              variant="subtle"
              color="red"
              aria-label={t('competition.removeTeam')}
              disabled={action.isPending}
              onClick={() => run(() => call(api.DELETE('/api/competitions/{id}/teams/{teamId}', { params: { path: { id, teamId: ct.teamId } } })))}
            >
              ✕
            </ActionIcon>
          </Group>
        ))
      ) : (
        <Text c="dimmed">{t('competition.noTeams')}</Text>
      )}
      <Group align="flex-end" gap="xs">
        <Select label={t('competition.addTeam')} data={options} value={teamId} onChange={setTeamId} searchable flex={1} />
        <Button
          disabled={!teamId || action.isPending}
          onClick={() => {
            if (teamId) add(teamId)
            setTeamId(null)
          }}
        >
          {t('common.add')}
        </Button>
      </Group>
      <Group
        component="form"
        align="flex-end"
        gap="xs"
        onSubmit={(e: FormEvent) => {
          e.preventDefault()
          run(() => createAndAdd(newTeam))
          setNewTeam('')
        }}
      >
        <TextInput label={t('competition.createTeam')} value={newTeam} maxLength={50} required onChange={(e) => setNewTeam(e.currentTarget.value)} flex={1} />
        <Button type="submit" variant="light" disabled={action.isPending}>
          {t('common.add')}
        </Button>
      </Group>
      <Button onClick={generate} loading={action.isPending}>
        {competition.status === 'ACTIVE' ? t('competition.regenerate') : t('competition.generate')}
      </Button>
    </Stack>
  )
}
