import { Badge, Button, Group, List, Loader, Stack, Text, TextInput, Title } from '@mantine/core'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { type FormEvent, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useParams } from 'react-router'
import { api, call, type Team } from '../../api/client'
import { errorCode, useNotifyError } from '../../api/errors'
import PlayerPicker from '../../components/PlayerPicker'

function TeamAdmin({ team }: { team: Team }) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const notifyError = useNotifyError()
  const [name, setName] = useState(team.name)
  const [roster, setRoster] = useState(team.players.map((p) => p.id))
  const path = { params: { path: { id: team.id } } }
  const saved = (updated: Team) => {
    queryClient.setQueryData(['team', team.id], updated)
    void queryClient.invalidateQueries({ queryKey: ['teams', 'mine'] })
    void queryClient.invalidateQueries({ queryKey: ['players', 'mine'] })
  }
  const rename = useMutation({
    mutationFn: () => call(api.PATCH('/api/teams/{id}', { ...path, body: { name } })),
    onSuccess: saved,
    onError: notifyError,
  })
  const saveRoster = useMutation({
    mutationFn: () => call(api.PUT('/api/teams/{id}/players', { ...path, body: { playerIds: roster } })),
    onSuccess: saved,
    onError: notifyError,
  })

  return (
    <Stack className="surface">
      <Group
        component="form"
        align="flex-end"
        gap="xs"
        onSubmit={(e: FormEvent) => {
          e.preventDefault()
          rename.mutate()
        }}
      >
        <TextInput label={t('teams.name')} value={name} maxLength={50} required onChange={(e) => setName(e.currentTarget.value)} />
        <Button type="submit" variant="light" loading={rename.isPending}>
          {t('teams.rename')}
        </Button>
      </Group>
      <PlayerPicker label={t('teams.roster')} value={roster} onChange={setRoster} />
      <Group>
        <Button onClick={() => saveRoster.mutate()} loading={saveRoster.isPending}>
          {t('teams.saveRoster')}
        </Button>
      </Group>
    </Stack>
  )
}

export default function TeamPage() {
  const { t } = useTranslation()
  const { id = '' } = useParams()
  const team = useQuery({
    queryKey: ['team', id],
    queryFn: () => call(api.GET('/api/teams/{id}', { params: { path: { id } } })),
    retry: false,
  })

  if (team.isPending) return <Loader />
  if (team.isError) return <Text>{t(`errors.${errorCode(team.error)}`, { defaultValue: t('errors.UNKNOWN') })}</Text>
  const data = team.data
  return (
    <Stack maw={600}>
      <Title order={2}>{data.name}</Title>
      {data.players.length ? (
        <List className="surface" listStyleType="none" spacing="xs">
          {data.players.map((p) => (
            <List.Item key={p.id}>
              {p.name}{' '}
              {!p.claimed && (
                <Badge size="xs" variant="light">
                  {t('players.guest')}
                </Badge>
              )}
            </List.Item>
          ))}
        </List>
      ) : (
        <Text c="dimmed">{t('teams.emptyRoster')}</Text>
      )}
      {data.canAdmin && <TeamAdmin key={data.id} team={data} />}
    </Stack>
  )
}
