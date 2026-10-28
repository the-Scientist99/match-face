import { Loader, Stack, Text, Title } from '@mantine/core'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { api, call, type MatchSummary } from '../../api/client'
import MatchList from '../../components/MatchList'
import { groupByRound } from '../matches/matchLogic'

export default function FixturesTab({ id }: { id: string }) {
  const { t } = useTranslation()
  const matches = useQuery<MatchSummary[]>({
    queryKey: ['competition', id, 'matches'],
    queryFn: () => call(api.GET('/api/competitions/{id}/matches', { params: { path: { id } } })),
  })
  if (matches.isPending) return <Loader />
  if (!matches.data?.length) return <Text c="dimmed">{t('competition.noFixtures')}</Text>
  return (
    <Stack>
      {groupByRound(matches.data).map(([round, ms]) => (
        <Stack key={round ?? 'none'} gap="xs">
          <Title order={5}>{round !== null ? t('competition.round', { round }) : t('competition.unscheduled')}</Title>
          <MatchList matches={ms} />
        </Stack>
      ))}
    </Stack>
  )
}
