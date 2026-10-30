import { Button, Card, Flex, Group, Loader, Pagination, SimpleGrid, Stack, Tabs, Text, TextInput, ThemeIcon, Title } from '@mantine/core'
import { useDebouncedValue } from '@mantine/hooks'
import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { type ReactNode, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useSearchParams } from 'react-router'
import { api, call, type CompetitionSummary, type MatchSummary, type OpenCompetitions, type OpenMatches } from '../../api/client'
import { ListIcon, PlusIcon, TrophyIcon } from '../../components/icons'
import Crest from '../../components/Crest'
import MatchList from '../../components/MatchList'
import { splitMatches } from '../matches/matchLogic'

function Section({ label, children }: { label: string; children: ReactNode }) {
  return (
    <Stack gap={10}>
      <Text component="h2" className="section-label" m={0}>
        {label}
      </Text>
      {children}
    </Stack>
  )
}

function CompetitionCard({ competition: c }: { competition: CompetitionSummary }) {
  const { t } = useTranslation()
  return (
    <Card component={Link} to={`/competitions/${c.id}`} padding="sm" radius={18} className="pressable">
      <Group wrap="nowrap" gap="sm">
        {c.imageUrl ? (
          <Crest name={c.name} size={40} src={c.imageUrl} />
        ) : (
          <ThemeIcon size={40} radius="md" variant="light" color={c.type === 'CUP' ? 'yellow' : 'teal'}>
            {c.type === 'CUP' ? <TrophyIcon /> : <ListIcon />}
          </ThemeIcon>
        )}
        <Stack gap={2} miw={0}>
          <Text fw={600} truncate>
            {c.name}
          </Text>
          <Text size="sm" c="dimmed">
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
    <Section label={label}>
      <TextInput
        type="search"
        aria-label={searchLabel}
        placeholder={searchLabel}
        value={search}
        maxLength={80}
        onChange={(e) => {
          setSearch(e.currentTarget.value)
          setPage(0)
        }}
      />
      {data.items.length ? children(data.items) : <Text c="dimmed">{noneFound}</Text>}
      {data.totalPages > 1 && (
        <Pagination
          total={data.totalPages}
          value={data.page + 1}
          onChange={(p) => setPage(p - 1)}
          getControlProps={(control) => ({ 'aria-label': t(control === 'previous' ? 'home.pagePrevious' : 'home.pageNext') })}
        />
      )}
    </Section>
  )
}

function MatchesTab() {
  const { t } = useTranslation()
  const mine = useQuery<MatchSummary[]>({ queryKey: ['matches', 'mine'], queryFn: () => call(api.GET('/api/matches/mine')) })
  const { upcoming, recent } = splitMatches(mine.data ?? [])
  return (
    <Stack gap={28}>
      {mine.isPending ? (
        <Loader />
      ) : (
        <div className="split">
          <Section label={upcoming.length ? `${t('home.upcoming')} · ${upcoming.length}` : t('home.upcoming')}>
            {upcoming.length ? <MatchList matches={upcoming} /> : <Text c="dimmed">{t('home.noUpcoming')}</Text>}
          </Section>
          <Section label={t('home.recent')}>
            {recent.length ? <MatchList matches={recent} /> : <Text c="dimmed">{t('home.noRecent')}</Text>}
          </Section>
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
    <Stack gap={28}>
      {mine.isPending ? (
        <Loader />
      ) : (
        <Section label={t('home.competitions')}>
          {mine.data?.length ? (
            <div className="h-scroll">
              {mine.data.map((c) => (
                <CompetitionCard key={c.id} competition={c} />
              ))}
            </div>
          ) : (
            <Text c="dimmed">{t('home.noCompetitions')}</Text>
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
          <SimpleGrid cols={{ base: 1, xs: 2, md: 3 }} spacing="xs">
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
  const [params, setParams] = useSearchParams()
  const tab = params.get('tab') === 'competitions' ? 'competitions' : 'matches'
  return (
    <Stack gap={28}>
      {/* wide screens: the actions sit beside the title instead of under it */}
      <Flex direction={{ base: 'column', md: 'row' }} justify="space-between" align={{ md: 'center' }} gap="md">
        <Title order={2}>{t('nav.home')}</Title>
        <SimpleGrid cols={2} spacing="xs" w={{ md: 400 }}>
          <Button component={Link} to="/matches/new" size="md" px="sm" leftSection={<PlusIcon size={18} />}>
            {t('match.new')}
          </Button>
          <Button component={Link} to="/competitions/new" size="md" px="sm" variant="light">
            {t('competition.new')}
          </Button>
        </SimpleGrid>
      </Flex>
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
