import { Badge, Button, Card, Group, Loader, Paper, Stack, Text, TextInput, Title } from '@mantine/core'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { type FormEvent, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useParams } from 'react-router'
import { api, call, type Team } from '../../api/client'
import { errorCode, useNotifyError } from '../../api/errors'
import Crest from '../../components/Crest'
import EditableCrest, { RemoveImageButton } from '../../components/ImageUpload'
import { ChevronIcon, PencilIcon } from '../../components/icons'
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
        wrap="nowrap"
        onSubmit={(e: FormEvent) => {
          e.preventDefault()
          rename.mutate()
        }}
      >
        <TextInput flex={1} label={t('teams.name')} value={name} maxLength={50} required onChange={(e) => setName(e.currentTarget.value)} />
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
  const [editing, setEditing] = useState(false)

  if (team.isPending) return <Loader />
  if (team.isError) return <Text>{t(`errors.${errorCode(team.error)}`, { defaultValue: t('errors.UNKNOWN') })}</Text>
  const data = team.data
  return (
    <Stack gap="lg">
      <Paper p="lg" pt="xl">
        <Stack gap="sm" align="center">
          {data.canAdmin ? (
            <EditableCrest kind="teams" id={data.id} name={data.name} imageUrl={data.imageUrl} size={72} />
          ) : (
            <Crest name={data.name} size={72} src={data.imageUrl} />
          )}
          <Title order={2} ta="center">
            {data.name}
          </Title>
          {data.canAdmin && data.imageUrl && <RemoveImageButton kind="teams" id={data.id} />}
        </Stack>
      </Paper>
      <Stack gap={10}>
        <Text component="h2" className="section-label" m={0}>
          {data.players.length ? `${t('teams.roster')} · ${data.players.length}` : t('teams.roster')}
        </Text>
        {data.players.length ? (
          <Card p={6} className="list-rows">
            {data.players.map((p) => (
              <Link key={p.id} to={`/players/${p.id}`} className="list-link">
                <Group gap="sm" wrap="nowrap" mih={56} px="sm">
                  <Crest name={p.name} size={32} src={p.imageUrl} />
                  <Text fw={600} flex={1} truncate>
                    {p.name}
                  </Text>
                  {!p.claimed && (
                    <Badge size="sm" color="gray">
                      {t('players.guest')}
                    </Badge>
                  )}
                </Group>
              </Link>
            ))}
          </Card>
        ) : (
          <Text c="dimmed">{t('teams.emptyRoster')}</Text>
        )}
      </Stack>
      {data.canAdmin && (
        <>
          {/* rename and roster fold behind one row, like the match page's lineup; an empty team opens it */}
          {data.players.length > 0 && (
            <Card component="button" type="button" className="pressable row-button" aria-expanded={editing} onClick={() => setEditing(!editing)}>
              <Group gap="sm" wrap="nowrap">
                <PencilIcon size={18} />
                <Text fw={600}>{t('teams.edit')}</Text>
                {editing ? (
                  <Text size="sm" fw={600} c="teal" ml="auto">
                    {t('event.done')}
                  </Text>
                ) : (
                  <Text c="dimmed" component="span" display="flex" ml="auto">
                    <ChevronIcon size={18} />
                  </Text>
                )}
              </Group>
            </Card>
          )}
          {(editing || !data.players.length) && <TeamAdmin key={data.id} team={data} />}
        </>
      )}
    </Stack>
  )
}
