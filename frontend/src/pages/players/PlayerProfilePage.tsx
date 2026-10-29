import { Badge, Button, Group, Loader, Paper, SimpleGrid, Stack, Text, TextInput, Title } from '@mantine/core'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { type FormEvent, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useParams } from 'react-router'
import { api, call, type PlayerProfile, type PlayerMatchRow } from '../../api/client'
import { errorCode, useNotifyError } from '../../api/errors'
import { useAuth } from '../../auth/AuthProvider'
import Crest from '../../components/Crest'
import { PencilIcon } from '../../components/icons'
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
      <Button variant="light" leftSection={<PencilIcon size={16} />} onClick={() => setName(profile.name)}>
        {t('players.rename')}
      </Button>
    )
  }
  return (
    <Group
      component="form"
      gap="xs"
      wrap="nowrap"
      flex={1}
      onSubmit={(e: FormEvent) => {
        e.preventDefault()
        rename.mutate(name)
      }}
    >
      <TextInput flex={1} aria-label={t('players.name')} value={name} maxLength={50} required autoFocus onChange={(e) => setName(e.currentTarget.value)} />
      <Button type="submit" loading={rename.isPending}>
        {t('common.save')}
      </Button>
      <Button variant="subtle" onClick={() => setName(null)}>
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
    // wide screens: who they are and their totals stay in view beside the match history
    <div className="split" data-aside="start">
      <div className="sticky-col">
        <Paper p="lg">
          <Stack gap="md">
            <Group gap="md" wrap="nowrap">
              <Crest name={p.name} size={64} />
              <Stack gap={6} miw={0} align="flex-start">
                <Title order={2} lh={1.15}>
                  {p.name}
                </Title>
                {!p.claimed && (
                  <Badge size="sm" color="gray">
                    {t('players.guest')}
                  </Badge>
                )}
              </Stack>
            </Group>
            {(p.canEdit || p.canInvite) && (
              <Group gap="xs">
                {p.canEdit && <Rename key={p.name} profile={p} />}
                {p.canInvite && <InviteButton playerId={p.id} />}
              </Group>
            )}
          </Stack>
        </Paper>
        <Paper p="md">
          <SimpleGrid cols={4} spacing="xs" verticalSpacing="lg">
            {TOTALS.map((k) => (
              // a zero is quieter, so the numbers that happened stand out
              <Stack key={k} gap={2} ta="center">
                <Text className="score" fz={30} fw={700} lh={1.1} c={p.totals[k] ? undefined : 'dimmed'}>
                  {p.totals[k]}
                </Text>
                <Text size="xs" c="dimmed" fw={500} lh={1.25}>
                  {t(`profile.${k}`)}
                </Text>
              </Stack>
            ))}
          </SimpleGrid>
        </Paper>
      </div>
      <div>
        <Text component="h2" className="section-label" m={0} mb={-8}>
          {t('profile.matches')}
        </Text>
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
      </div>
    </div>
  )
}
