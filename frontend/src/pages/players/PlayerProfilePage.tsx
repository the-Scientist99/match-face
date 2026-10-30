import { Anchor, Button, Group, Paper, Stack, Tabs, Text, TextInput } from '@mantine/core'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { type FormEvent, type ReactNode, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useParams } from 'react-router'
import { api, call, type PlayerMatchRow, type PlayerProfile } from '../../api/client'
import { useNotifyError } from '../../api/errors'
import { useAuth } from '../../auth/AuthProvider'
import LoadError from '../../auth/LoadError'
import Crest from '../../components/Crest'
import Empty from '../../components/Empty'
import { PencilIcon } from '../../components/icons'
import { PlayerPhoto, RemoveImageButton } from '../../components/ImageUpload'
import InviteButton from '../../components/InviteButton'
import Loading from '../../components/Loading'
import MatchList from '../../components/MatchList'
import PitchLines from '../../components/PitchLines'
import { usePageTitle } from '../../components/usePageTitle'
import { htmlLang, type Lang } from '../../lang'
import { resultFor } from '../matches/matchLogic'

const TOTALS = ['appearances', 'goals', 'assists', 'cleanSheets', 'manOfTheMatch', 'yellowCards', 'redCards', 'ownGoals'] as const

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
      <Button variant="default" leftSection={<PencilIcon size={16} />} onClick={() => setName(profile.name)}>
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

/** "Goals: 2 · Assists: 1", or nothing for a match with neither. */
function useContribution() {
  const { t } = useTranslation()
  return ({ goals, assists }: { goals: number; assists: number }) =>
    [goals > 0 && `${t('profile.goals')}: ${goals}`, assists > 0 && `${t('profile.assists')}: ${assists}`].filter(Boolean).join(' · ')
}

/** One opponent in the Form or Upcoming panel. */
function Opponent({ row, children }: { row: PlayerMatchRow; children: ReactNode }) {
  const { t } = useTranslation()
  const m = row.match
  const home = row.side === 'HOME'
  const name = home ? m.awayName : m.homeName
  return (
    <li style={{ minWidth: 0 }}>
      <Link to={`/matches/${m.id}`}>
        <Crest name={name} src={home ? m.awayImageUrl : m.homeImageUrl} size={48} />
        <Text size="sm" fw={600} lh={1.2}>
          {name ?? t('bracket.tbd')}
        </Text>
        <Text size="xs" c="dimmed" lh={1.2}>
          {t(home ? 'match.home' : 'match.away')}
        </Text>
        {children}
      </Link>
    </li>
  )
}

/** The last three results oldest first, so the newest is on the right. */
function Form({ rows }: { rows: PlayerMatchRow[] }) {
  const { t } = useTranslation()
  const contribution = useContribution()
  return (
    <Paper component="section" p="lg" flex="1 1 320px" miw={0} aria-labelledby="form-title">
      <Stack gap="sm">
        <h2 id="form-title" className="section-title">
          {t('profile.form')}
        </h2>
        <ol className="form-grid">
          {rows
            .slice(0, 3)
            .reverse()
            .map((r) => {
              const result = resultFor(r.match.score, r.side)
              return (
                <Opponent key={r.match.id} row={r}>
                  <abbr className="score result-pill" data-result={result} title={t(`table.${result}Full`)} style={{ textDecoration: 'none' }}>
                    {t(`table.${result}`)} {r.match.score.home}–{r.match.score.away}
                  </abbr>
                  <Text size="xs" c="dimmed">
                    {contribution(r)}
                  </Text>
                </Opponent>
              )
            })}
        </ol>
      </Stack>
    </Paper>
  )
}

function Upcoming({ rows }: { rows: PlayerMatchRow[] }) {
  const { t, i18n } = useTranslation()
  const day = (iso: string | null) => iso && new Date(iso).toLocaleDateString(htmlLang(i18n.language as Lang), { weekday: 'short', day: 'numeric', month: 'short' })
  return (
    <Paper component="section" p="lg" flex="1 1 320px" miw={0} aria-labelledby="upcoming-title">
      <Stack gap="sm">
        <h2 id="upcoming-title" className="section-title">
          {t('home.upcoming')}
        </h2>
        <ol className="form-grid">
          {rows.map((r) => (
            <Opponent key={r.match.id} row={r}>
              <Text component="time" size="xs" fw={600} dateTime={r.match.kickoffAt ?? undefined}>
                {day(r.match.kickoffAt)}
              </Text>
            </Opponent>
          ))}
        </ol>
      </Stack>
    </Paper>
  )
}

export default function PlayerProfilePage() {
  const { t } = useTranslation()
  const { id = '' } = useParams()
  const [tab, setTab] = useState<string | null>('matches')
  const contribution = useContribution()
  const profile = useQuery<PlayerProfile>({
    queryKey: ['player', id],
    queryFn: () => call(api.GET('/api/players/{id}', { params: { path: { id } } })),
    retry: false,
  })
  usePageTitle(profile.data?.name)

  if (profile.isPending) return <Loading />
  if (profile.isError) return <LoadError error={profile.error} />
  const p = profile.data
  const rows = new Map<string, PlayerMatchRow>(p.matches.map((r) => [r.match.id, r]))
  // first names light, the surname heavy; a single name is all surname
  const words = p.name.trim().split(/\s+/)
  const last = words.pop()
  const perMatch = (n: number) => t('profile.perMatch', { value: (n / p.totals.appearances).toFixed(2) })
  const played = p.totals.appearances > 0

  return (
    <Stack gap="lg">
      <section className="board bleed player-hero" aria-labelledby="player-name">
        <PitchLines kind="field" />
        <div className="bleed-inner">
          <PlayerPhoto kind="players" id={p.id} name={p.name} imageUrl={p.imageUrl} canEdit={p.canEdit} />
          <div className="player-hero-text">
            {!p.claimed && <span className="status-chip">{t('players.guest')}</span>}
            <h1 id="player-name" className="player-name">
              {words.length > 0 && <span className="player-name-first">{words.join(' ')}</span>}
              <span className="player-name-last">{last}</span>
            </h1>
            {p.teams.length > 0 && (
              <Group gap="md" mih={44}>
                {p.teams.map((team) => (
                  <Anchor key={team.id} component={Link} to={`/teams/${team.id}`} c="dimmed" size="lg" fw={500} display="inline-flex" mih={44} style={{ alignItems: 'center' }}>
                    {team.name}
                  </Anchor>
                ))}
              </Group>
            )}
          </div>
        </div>
      </section>

      {(p.canEdit || p.canInvite) && (
        <Group gap="xs">
          {p.canEdit && <Rename key={p.name} profile={p} />}
          {p.canEdit && p.imageUrl && <RemoveImageButton kind="players" id={p.id} />}
          {p.canInvite && <InviteButton playerId={p.id} />}
        </Group>
      )}

      <Paper p="lg" px="xs">
        <dl className="stat-trio">
          <div>
            <dt>{t('profile.goals')}</dt>
            <dd className="score stat-trio-value">{p.totals.goals}</dd>
            {played && <dd className="stat-trio-note">{perMatch(p.totals.goals)}</dd>}
          </div>
          <div>
            <dt>{t('profile.assists')}</dt>
            <dd className="score stat-trio-value">{p.totals.assists}</dd>
            {played && <dd className="stat-trio-note">{perMatch(p.totals.assists)}</dd>}
          </div>
          <div>
            <dt>{t('profile.appearances')}</dt>
            <dd className="score stat-trio-value">{p.totals.appearances}</dd>
            {p.totals.cleanSheets > 0 && (
              <dd className="stat-trio-note">
                {t('profile.cleanSheets')}: {p.totals.cleanSheets}
              </dd>
            )}
          </div>
        </dl>
      </Paper>

      {(p.matches.length > 0 || p.upcoming.length > 0) && (
        <Group gap="lg" align="stretch">
          {p.matches.length > 0 && <Form rows={p.matches} />}
          {p.upcoming.length > 0 && <Upcoming rows={p.upcoming} />}
        </Group>
      )}

      <Tabs className="track-tabs" value={tab} onChange={setTab}>
        <Tabs.List>
          <Tabs.Tab value="matches">{t('profile.matches')}</Tabs.Tab>
          <Tabs.Tab value="stats">{t('competition.stats')}</Tabs.Tab>
        </Tabs.List>
        <Tabs.Panel value="matches" pt="md">
          {p.matches.length ? (
            <MatchList
              matches={p.matches.map((r) => r.match)}
              extra={(m) => {
                const r = rows.get(m.id)
                const text = r && contribution(r)
                return text ? (
                  <Text size="xs" fw={600} mt={6}>
                    {text}
                  </Text>
                ) : null
              }}
            />
          ) : (
            <Empty text={t('profile.noMatches')} />
          )}
        </Tabs.Panel>
        <Tabs.Panel value="stats" pt="md">
          <dl className="totals-grid">
            {TOTALS.map((k) => (
              <div key={k}>
                <dt>{t(`profile.${k}`)}</dt>
                {/* a zero is quieter, so the numbers that happened stand out */}
                <dd className="score" data-zero={p.totals[k] ? undefined : true}>
                  {p.totals[k]}
                </dd>
              </div>
            ))}
          </dl>
        </Tabs.Panel>
      </Tabs>
    </Stack>
  )
}
