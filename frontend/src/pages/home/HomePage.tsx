import { Button, Card, Flex, Group, Loader, Pagination, SimpleGrid, Stack, Text, TextInput, ThemeIcon, Title } from '@mantine/core'
import { useDebouncedValue } from '@mantine/hooks'
import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { type ReactNode, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { api, call, type CompetitionSummary, type MatchSummary, type OpenCompetitions } from '../../api/client'
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

/** Hidden until there's something to find; once searching it stays, so the box doesn't vanish on no matches. */
function OpenCompetitionsSection() {
  const { t } = useTranslation()
  const [search, setSearch] = useState('')
  const [q] = useDebouncedValue(search.trim(), 300)
  const [page, setPage] = useState(0)
  const open = useQuery<OpenCompetitions>({
    queryKey: ['competitions', 'open', q, page],
    queryFn: () => call(api.GET('/api/competitions/open', { params: { query: { q, page } } })),
    placeholderData: keepPreviousData,
  })
  const data = open.data
  if (!data || (!data.items.length && !search)) return null
  return (
    <Section label={t('home.openCompetitions')}>
      <TextInput
        type="search"
        aria-label={t('home.searchCompetitions')}
        placeholder={t('home.searchCompetitions')}
        value={search}
        maxLength={80}
        onChange={(e) => {
          setSearch(e.currentTarget.value)
          setPage(0)
        }}
      />
      {data.items.length ? (
        <SimpleGrid cols={{ base: 1, xs: 2, md: 3 }} spacing="xs">
          {data.items.map((c) => (
            <CompetitionCard key={c.id} competition={c} />
          ))}
        </SimpleGrid>
      ) : (
        <Text c="dimmed">{t('home.noCompetitionsFound')}</Text>
      )}
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

export default function HomePage() {
  const { t } = useTranslation()
  const mine = useQuery<MatchSummary[]>({ queryKey: ['matches', 'mine'], queryFn: () => call(api.GET('/api/matches/mine')) })
  const competitions = useQuery<CompetitionSummary[]>({
    queryKey: ['competitions', 'mine'],
    queryFn: () => call(api.GET('/api/competitions/mine')),
  })
  const open = useQuery<MatchSummary[]>({ queryKey: ['matches', 'open'], queryFn: () => call(api.GET('/api/matches/open')) })
  const { upcoming, recent } = splitMatches(mine.data ?? [])
  return (
    <Stack gap={28}>
      {/* wide screens: the actions sit beside the title instead of under it */}
      <Flex direction={{ base: 'column', md: 'row' }} justify="space-between" align={{ md: 'center' }} gap="md">
        <Title order={2}>{t('home.title')}</Title>
        <SimpleGrid cols={2} spacing="xs" w={{ md: 400 }}>
          <Button component={Link} to="/matches/new" size="md" px="sm" leftSection={<PlusIcon size={18} />}>
            {t('match.new')}
          </Button>
          <Button component={Link} to="/competitions/new" size="md" px="sm" variant="light">
            {t('competition.new')}
          </Button>
        </SimpleGrid>
      </Flex>
      {!!competitions.data?.length && (
        <Section label={t('home.competitions')}>
          <div className="h-scroll">
            {competitions.data.map((c) => (
              <CompetitionCard key={c.id} competition={c} />
            ))}
          </div>
        </Section>
      )}
      <OpenCompetitionsSection />
      {!!open.data?.length && (
        <Section label={t('home.open')}>
          <MatchList matches={open.data} />
        </Section>
      )}
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
    </Stack>
  )
}
