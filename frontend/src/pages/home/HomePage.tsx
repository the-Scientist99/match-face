import { Anchor, Button, Group, Loader, Stack, Text, Title } from '@mantine/core'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { api, call, type CompetitionSummary, type MatchSummary } from '../../api/client'
import MatchList from '../../components/MatchList'
import { splitMatches } from '../matches/matchLogic'

export default function HomePage() {
  const { t } = useTranslation()
  const mine = useQuery<MatchSummary[]>({ queryKey: ['matches', 'mine'], queryFn: () => call(api.GET('/api/matches/mine')) })
  const competitions = useQuery<CompetitionSummary[]>({
    queryKey: ['competitions', 'mine'],
    queryFn: () => call(api.GET('/api/competitions/mine')),
  })
  const { upcoming, recent } = splitMatches(mine.data ?? [])
  return (
    <Stack maw={600}>
      <Group justify="space-between">
        <Title order={2}>{t('home.title')}</Title>
        <Group gap="xs">
          <Button component={Link} to="/matches/new">
            {t('match.new')}
          </Button>
          <Button component={Link} to="/competitions/new" variant="light">
            {t('competition.new')}
          </Button>
        </Group>
      </Group>
      {!!competitions.data?.length && (
        <>
          <Title order={4}>{t('home.competitions')}</Title>
          {competitions.data.map((c) => (
            <Anchor key={c.id} component={Link} to={`/competitions/${c.id}`}>
              {c.name}
            </Anchor>
          ))}
        </>
      )}
      {mine.isPending ? (
        <Loader />
      ) : (
        <>
          <Title order={4}>{t('home.upcoming')}</Title>
          {upcoming.length ? <MatchList matches={upcoming} /> : <Text c="dimmed">{t('home.noUpcoming')}</Text>}
          <Title order={4}>{t('home.recent')}</Title>
          {recent.length ? <MatchList matches={recent} /> : <Text c="dimmed">{t('home.noRecent')}</Text>}
        </>
      )}
    </Stack>
  )
}
