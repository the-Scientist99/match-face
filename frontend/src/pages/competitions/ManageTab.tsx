import { ActionIcon, Button, Checkbox, Group, NumberInput, Select, Stack, Text, TextInput } from '@mantine/core'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { type FormEvent, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { api, call, type Competition } from '../../api/client'
import { useNotifyError } from '../../api/errors'
import { CloseIcon } from '../../components/icons'

/** Teams in the competition, adding (existing or new) and removing them, entries from others, and generating fixtures. */
export default function ManageTab({ competition }: { competition: Competition }) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const notifyError = useNotifyError()
  const id = competition.id
  const path = { params: { path: { id } } }
  const [teamId, setTeamId] = useState<string | null>(null)
  const [seed, setSeed] = useState<number | string>('')
  const cup = competition.type === 'CUP'
  const seedValue = cup && typeof seed === 'number' ? seed : undefined
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
  // Any team change or generation in an ACTIVE league replaces every fixture (dates, lineups, live events).
  const confirmed = () => competition.status !== 'ACTIVE' || window.confirm(t('competition.regenerateWarning'))
  const run = (fn: () => Promise<unknown>) => {
    if (confirmed()) action.mutate(fn as () => Promise<Competition>)
  }
  const add = (team: string) => run(() => call(api.POST('/api/competitions/{id}/teams', { ...path, body: { teamId: team, seed: seedValue } })))
  const createAndAdd = async (name: string) => {
    const team = await call(api.POST('/api/teams', { body: { name } }))
    void queryClient.invalidateQueries({ queryKey: ['teams', 'mine'] })
    return call(api.POST('/api/competitions/{id}/teams', { ...path, body: { teamId: team.id, seed: seedValue } }))
  }
  const generate = () => {
    if (competition.entries.length === 0 || window.confirm(t('competition.entriesDropWarning'))) run(() => call(api.POST('/api/competitions/{id}/generate', path)))
  }
  const entryPath = (teamId: string) => ({ params: { path: { id, teamId } } })

  return (
    <Stack className="surface">
      {competition.visibility === 'PUBLIC' && competition.status === 'DRAFT' && (
        <Checkbox
          label={t('competition.entriesOpen')}
          description={t('competition.entriesOpenHint')}
          checked={competition.entriesOpen}
          disabled={action.isPending}
          onChange={(e) => {
            const entriesOpen = e.currentTarget.checked
            run(() => call(api.PATCH('/api/competitions/{id}', { ...path, body: { version: competition.version, entriesOpen } })))
          }}
        />
      )}
      {competition.entries.length > 0 && <h2 className="section-title">{t('competition.entriesTitle')}</h2>}
      {competition.entries.map((entry) => (
        <Group key={entry.teamId} justify="space-between">
          <Text>{entry.name}</Text>
          <Group gap="xs">
            <Button
              disabled={action.isPending}
              onClick={() => {
                run(() => call(api.POST('/api/competitions/{id}/entries/{teamId}/approve', { ...entryPath(entry.teamId), body: { seed: seedValue } })))
                setSeed('')
              }}
            >
              {t('competition.approve')}
            </Button>
            <ActionIcon
              variant="subtle"
              color="red"
              aria-label={t('competition.reject')}
              disabled={action.isPending}
              onClick={() => run(() => call(api.DELETE('/api/competitions/{id}/entries/{teamId}', entryPath(entry.teamId))))}
            >
              <CloseIcon size={16} />
            </ActionIcon>
          </Group>
        </Group>
      ))}
      <h2 className="section-title">{t('competition.teams')}</h2>
      {competition.teams.length ? (
        competition.teams.map((ct) => (
          <Group key={ct.teamId} justify="space-between">
            <Text>
              {ct.name}
              {cup && ct.seed !== null && ` (${t('competition.seed')} ${ct.seed})`}
            </Text>
            <ActionIcon
              variant="subtle"
              color="red"
              aria-label={t('competition.removeTeam')}
              disabled={action.isPending}
              onClick={() => run(() => call(api.DELETE('/api/competitions/{id}/teams/{teamId}', { params: { path: { id, teamId: ct.teamId } } })))}
            >
              <CloseIcon size={16} />
            </ActionIcon>
          </Group>
        ))
      ) : (
        <Text c="dimmed">{t('competition.noTeams')}</Text>
      )}
      {cup && (
        <NumberInput
          label={t('competition.seed')}
          description={t('competition.seedHint')}
          min={1}
          max={64}
          allowDecimal={false}
          value={seed}
          onChange={setSeed}
          w={160}
        />
      )}
      <Group align="flex-end" gap="xs">
        <Select label={t('competition.addTeam')} data={options} value={teamId} onChange={setTeamId} searchable flex={1} />
        <Button
          disabled={!teamId || action.isPending}
          onClick={() => {
            if (teamId) add(teamId)
            setTeamId(null)
            setSeed('')
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
