import { Badge, Button, Group, Loader, Paper, SimpleGrid, Stack, Text, TextInput, Title } from '@mantine/core'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { type FormEvent, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useParams } from 'react-router'
import { api, call, type PlayerProfile, type PlayerMatchRow } from '../../api/client'
import { errorCode, useNotifyError } from '../../api/errors'
import { useAuth } from '../../auth/AuthProvider'
import InviteButton from '../../components/InviteButton'
import MatchList from '../../components/MatchList'

const TOTALS = ['appearances', 'goals', 'assists', 'cleanSheets', 'yellowCards', 'redCards', 'manOfTheMatch', 'ownGoals'] as const

function Rename({ profile }: { profile: PlayerProfile }) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const notifyError = useNotifyError()
  const { user, refreshMe } = useAuth()
  const [name, setName] = useState<string | null>(null)
  const rename = useMutation({
    mutationFn: (n: string) => call(api.PATCH('/api/players/{id}', { params: { path: { id: profile.id } }, body: { name: n } })),
    onSuccess: () => {
      setName(null)
      void queryClient.invalidateQueries({ queryKey: ['player', profile.id] })
      void queryClient.invalidateQueries({ queryKey: ['players', 'mine'] })
      if (profile.id === user?.playerId) void refreshMe()
    },
    onError: notifyError,
  })
  if (name === null) {
    return (
      <Button variant="subtle" size="xs" onClick={() => setName(profile.name)}>
        {t('players.rename')}
      </Button>
    )
  }
  return (
    <Group
      component="form"
      gap="xs"
      onSubmit={(e: FormEvent) => {
        e.preventDefault()
        rename.mutate(name)
      }}
    >
      <TextInput aria-label={t('players.name')} value={name} maxLength={50} required autoFocus onChange={(e) => setName(e.currentTarget.value)} />
      <Button type="submit" size="xs" loading={rename.isPending}>
        {t('common.save')}
      </Button>
      <Button size="xs" variant="subtle" onClick={() => setName(null)}>
        {t('common.cancel')}
      </Button>
    </Group>
  )
}

export default function PlayerProfilePage() {
  const { t } = useTranslation()
  const { id = '' } = useParams()
  const profile = useQuery<PlayerProfile>({
    queryKey: ['player', id],
    queryFn: () => call(api.GET('/api/players/{id}', { params: { path: { id } } })),
    retry: false,
  })

  if (profile.isPending) return <Loader />
  if (profile.isError) return <Text>{t(`errors.${errorCode(profile.error)}`, { defaultValue: t('errors.UNKNOWN') })}</Text>
  const p = profile.data
  const rows = new Map<string, PlayerMatchRow>(p.matches.map((r) => [r.match.id, r]))

  return (
    <Stack maw={700}>
      <Group gap="xs">
        <Title order={2}>{p.name}</Title>
        {!p.claimed && <Badge variant="light">{t('players.guest')}</Badge>}
      </Group>
      <Group gap="xs">
        {p.canEdit && <Rename key={p.name} profile={p} />}
        {p.canInvite && <InviteButton playerId={p.id} />}
      </Group>
      <SimpleGrid cols={{ base: 2, xs: 4 }}>
        {TOTALS.map((k) => (
          <Paper key={k} withBorder p="sm" ta="center">
            <Text size="xl" fw={700}>
              {p.totals[k]}
            </Text>
            <Text size="xs" c="dimmed">
              {t(`profile.${k}`)}
            </Text>
          </Paper>
        ))}
      </SimpleGrid>
      <Title order={4}>{t('profile.matches')}</Title>
      {p.matches.length ? (
        <MatchList
          matches={p.matches.map((r) => r.match)}
          extra={(m) => {
            const r = rows.get(m.id)
            return r && (r.goals || r.assists) ? (
              <Text size="sm">{t('profile.inMatch', { goals: r.goals, assists: r.assists })}</Text>
            ) : null
          }}
        />
      ) : (
        <Text c="dimmed">{t('profile.noMatches')}</Text>
      )}
    </Stack>
  )
}
