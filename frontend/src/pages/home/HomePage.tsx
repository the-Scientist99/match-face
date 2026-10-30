import { Button, Card, Group, Pagination, SimpleGrid, Stack, Tabs, Text, TextInput, ThemeIcon, Title } from '@mantine/core'
import { useDebouncedValue } from '@mantine/hooks'
import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { type ReactNode, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useSearchParams } from 'react-router'
import { api, call, type CompetitionSummary, type MatchSummary, type OpenCompetitions, type OpenMatches } from '../../api/client'
import LoadError from '../../auth/LoadError'
import Crest from '../../components/Crest'
import Empty from '../../components/Empty'
import { ListIcon, SearchIcon, TrophyIcon } from '../../components/icons'
import Loading from '../../components/Loading'
import MatchList, { formatKickoff } from '../../components/MatchList'
import PitchLines from '../../components/PitchLines'
import { usePageTitle } from '../../components/usePageTitle'
import { isLive, splitMatches } from '../matches/matchLogic'

function Section({ title, count, aside, children }: { title: string; count?: number; aside?: ReactNode; children: ReactNode }) {
  return (
    <Stack gap={12} miw={0}>
      <Group justify="space-between" gap="md">
        <h2 className="section-title">
          {title} {!!count && <span>· {count}</span>}
        </h2>
        {aside}
      </Group>
      {children}
    </Stack>
  )
}

function CompetitionCard({ competition: c }: { competition: CompetitionSummary }) {
  const { t } = useTranslation()
  return (
    <Card component={Link} to={`/competitions/${c.id}`} padding="sm" px="md" className="pressable">
      <Group wrap="nowrap" gap="sm">
        {c.imageUrl ? (
          <Crest name={c.name} size={44} src={c.imageUrl} />
        ) : (
          <ThemeIcon size={44} radius="md" variant="light" color={c.type === 'CUP' ? 'yellow' : 'teal'}>
            {c.type === 'CUP' ? <TrophyIcon /> : <ListIcon />}
          </ThemeIcon>
        )}
        <Stack gap={2} miw={0}>
          <Text fw={600} truncate>
            {c.name}
          </Text>
          <Text size="xs" c="dimmed">
            {t(`competition.type.${c.type}`)} · {t(`competition.status.${c.status}`)}
          </Text>
        </Stack>
      </Group>
    </Card>
  )
}

type Paged<T> = { items: T[]; page: number; totalPages: number }

type OpenListProps<T> = {
  label: string
  searchLabel: string
  noneFound: string
  queryKey: string[]
  fetch: (q: string, page: number) => Promise<Paged<T>>
  children: (items: T[]) => ReactNode
}

/** Hidden until there's something to find; once searching it stays, so the box doesn't vanish on no matches. */
function OpenList<T>({ label, searchLabel, noneFound, queryKey, fetch, children }: OpenListProps<T>) {
  const { t } = useTranslation()
  const [search, setSearch] = useState('')
  const [q] = useDebouncedValue(search.trim(), 300)
  const [page, setPage] = useState(0)
  const { data } = useQuery({ queryKey: [...queryKey, q, page], queryFn: () => fetch(q, page), placeholderData: keepPreviousData })
  if (!data || (!data.items.length && !search)) return null
  return (
    <Section
      title={label}
      aside={
        <TextInput
          type="search"
          flex="1 1 280px"
          maw={340}
          aria-label={searchLabel}
          placeholder={searchLabel}
          leftSection={<SearchIcon size={18} />}
          value={search}
          maxLength={80}
          onChange={(e) => {
            setSearch(e.currentTarget.value)
            setPage(0)
          }}
        />
      }
    >
      {data.items.length ? children(data.items) : <Empty text={noneFound} />}
      {data.totalPages > 1 && (
        <Group justify="center">
          <Pagination
            total={data.totalPages}
            value={data.page + 1}
            onChange={(p) => setPage(p - 1)}
            getControlProps={(control) => ({ 'aria-label': t(control === 'previous' ? 'home.pagePrevious' : 'home.pageNext') })}
          />
        </Group>
      )}
    </Section>
  )
}

/** The soonest match with a date, on the screen's one dark board. */
function NextMatch({ match: m }: { match: MatchSummary }) {
  const { t, i18n } = useTranslation()
  return (
    <section className="board" aria-labelledby="next-match">
      <PitchLines />
      <Group justify="space-between" gap="lg">
        <Stack gap={2}>
          <h2 id="next-match" className="board-eyebrow" style={{ margin: 0 }}>
            {t(isLive(m) ? 'match.status.LIVE' : 'home.next')}
          </h2>
          <Text component="time" dateTime={m.kickoffAt!} className="score" fz={32} lh={1.1}>
            {formatKickoff(m.kickoffAt, i18n.language)}
          </Text>
          {m.competitionName && (
            <Text size="sm" c="dimmed">
              {m.competitionName}
              {m.round !== null && ` · ${t('competition.round', { round: m.round })}`}
            </Text>
          )}
        </Stack>
        <Stack gap={8} flex="1 1 240px" miw={0}>
          <Group gap={10} wrap="nowrap">
            <Crest name={m.homeName} src={m.homeImageUrl} size={36} />
            <Text fw={600} truncate>
              {m.homeName}
            </Text>
          </Group>
          <Group gap={10} wrap="nowrap">
            <Crest name={m.awayName} src={m.awayImageUrl} size={36} />
            <Text fw={600} truncate>
              {m.awayName}
            </Text>
          </Group>
        </Stack>
        <Button component={Link} to={`/matches/${m.id}`} variant="white" color="dark">
          {t('home.openMatch')}
        </Button>
      </Group>
    </section>
  )
}

function useMyMatches() {
  return useQuery<MatchSummary[]>({ queryKey: ['matches', 'mine'], queryFn: () => call(api.GET('/api/matches/mine')) })
}

function MatchesTab() {
  const { t } = useTranslation()
  const mine = useMyMatches()
  const { upcoming, recent } = splitMatches(mine.data ?? [])
  // the board above the tabs shows the first one
  const later = upcoming[0]?.kickoffAt ? upcoming.slice(1) : upcoming
  return (
    <Stack gap={32}>
      {mine.isPending ? (
        <Loading />
      ) : mine.isError ? (
        <LoadError error={mine.error} onRetry={() => void mine.refetch()} />
      ) : (
        <div className="split">
          {later.length > 0 ? (
            <Section title={t('home.upcoming')} count={later.length}>
              <MatchList matches={later} />
            </Section>
          ) : (
            !upcoming.length && (
              <Section title={t('home.upcoming')}>
                <Empty
                  text={t('home.noUpcoming')}
                  action={
                    <Button component={Link} to="/matches/new">
                      {t('match.new')}
                    </Button>
                  }
                />
              </Section>
            )
          )}
          <Section title={t('home.recent')}>{recent.length ? <MatchList matches={recent} /> : <Empty text={t('home.noRecent')} />}</Section>
        </div>
      )}
      <OpenList<MatchSummary>
        label={t('home.open')}
        searchLabel={t('home.searchMatches')}
        noneFound={t('home.noMatchesFound')}
        queryKey={['matches', 'open']}
        fetch={(q, page) => call(api.GET('/api/matches/open', { params: { query: { q, page } } })) as Promise<OpenMatches>}
      >
        {(matches) => <MatchList matches={matches} />}
      </OpenList>
    </Stack>
  )
}

function CompetitionsTab() {
  const { t } = useTranslation()
  const mine = useQuery<CompetitionSummary[]>({
    queryKey: ['competitions', 'mine'],
    queryFn: () => call(api.GET('/api/competitions/mine')),
  })
  return (
    <Stack gap={32}>
      {mine.isPending ? (
        <Loading />
      ) : mine.isError ? (
        <LoadError error={mine.error} onRetry={() => void mine.refetch()} />
      ) : (
        <Section title={t('home.competitions')}>
          {mine.data.length ? (
            <SimpleGrid cols={{ base: 1, xs: 2, md: 3 }} spacing="sm">
              {mine.data.map((c) => (
                <CompetitionCard key={c.id} competition={c} />
              ))}
            </SimpleGrid>
          ) : (
            <Empty
              text={t('home.noCompetitions')}
              action={
                <Button component={Link} to="/competitions/new">
                  {t('competition.new')}
                </Button>
              }
            />
          )}
        </Section>
      )}
      <OpenList<CompetitionSummary>
        label={t('home.openCompetitions')}
        searchLabel={t('home.searchCompetitions')}
        noneFound={t('home.noCompetitionsFound')}
        queryKey={['competitions', 'open']}
        fetch={(q, page) => call(api.GET('/api/competitions/open', { params: { query: { q, page } } })) as Promise<OpenCompetitions>}
      >
        {(competitions) => (
          <SimpleGrid cols={{ base: 1, xs: 2, md: 3 }} spacing="sm">
            {competitions.map((c) => (
              <CompetitionCard key={c.id} competition={c} />
            ))}
          </SimpleGrid>
        )}
      </OpenList>
    </Stack>
  )
}

export default function HomePage() {
  const { t } = useTranslation()
  usePageTitle(t('nav.home'))
  const [params, setParams] = useSearchParams()
  const tab = params.get('tab') === 'competitions' ? 'competitions' : 'matches'
  const next = splitMatches(useMyMatches().data ?? []).upcoming[0]
  return (
    <Stack gap="xl">
      <Group justify="space-between" gap="sm">
        <Title order={1}>{t('nav.home')}</Title>
        <Button component={Link} to="/competitions/new" variant="default">
          {t('competition.new')}
        </Button>
      </Group>
      {next?.kickoffAt && <NextMatch match={next} />}
      <Tabs className="track-tabs" value={tab} onChange={(v) => setParams(v === 'competitions' ? { tab: v } : {}, { replace: true })}>
        <Tabs.List>
          <Tabs.Tab value="matches">{t('nav.matches')}</Tabs.Tab>
          <Tabs.Tab value="competitions">{t('home.tabCompetitions')}</Tabs.Tab>
        </Tabs.List>
        <Tabs.Panel value="matches" pt="lg">
          <MatchesTab />
        </Tabs.Panel>
        <Tabs.Panel value="competitions" pt="lg">
          <CompetitionsTab />
        </Tabs.Panel>
      </Tabs>
    </Stack>
  )
}
