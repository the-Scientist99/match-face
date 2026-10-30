import { Card, Group, Paper, ScrollArea, Stack, Text } from '@mantine/core'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { api, call, type Bracket, type BracketMatch } from '../../api/client'
import LoadError from '../../auth/LoadError'
import Empty from '../../components/Empty'
import { TrophyIcon } from '../../components/icons'
import Loading from '../../components/Loading'
import { roundLabel } from '../matches/matchLogic'

/** An empty slot waits for an earlier round (bye teams are placed straight into round 2). */
function Side({ name, score, won, pens }: { name: string | null; score: number | null; won: boolean; pens: boolean }) {
  const { t } = useTranslation()
  return (
    <Group justify="space-between" wrap="nowrap" gap="xs">
      <Text size="sm" fw={won ? 700 : undefined} c={name ? undefined : 'dimmed'} truncate>
        {name ?? t('bracket.tbd')}
        {pens && ` (${t('bracket.pens')})`}
      </Text>
      {score !== null && (
        <Text size="sm" className="score" fw={won ? 700 : undefined}>
          {score}
        </Text>
      )}
    </Group>
  )
}

function MatchCard({ bm }: { bm: BracketMatch }) {
  const m = bm.match
  const played = m.status === 'FINISHED'
  const winner = !played ? null : m.score.home > m.score.away ? 'HOME' : m.score.away > m.score.home ? 'AWAY' : bm.penaltyWinner
  return (
    <Card padding="sm" w={200} className="pressable" component={Link} to={`/matches/${m.id}`}>
      <Side name={m.homeName} score={played ? m.score.home : null} won={winner === 'HOME'} pens={bm.penaltyWinner === 'HOME'} />
      <Side name={m.awayName} score={played ? m.score.away : null} won={winner === 'AWAY'} pens={bm.penaltyWinner === 'AWAY'} />
    </Card>
  )
}

/** One column per round, scrolling sideways on phones. */
export default function BracketTab({ id }: { id: string }) {
  const { t } = useTranslation()
  const bracket = useQuery<Bracket>({
    queryKey: ['competition', id, 'bracket'],
    queryFn: () => call(api.GET('/api/competitions/{id}/bracket', { params: { path: { id } } })) as Promise<Bracket>,
  })
  if (bracket.isPending) return <Loading />
  if (bracket.isError) return <LoadError error={bracket.error} onRetry={() => void bracket.refetch()} />
  const b = bracket.data
  if (!b.matches.length) return <Empty text={t('competition.noFixtures')} />
  const rounds = Array.from({ length: b.rounds }, (_, i) => i + 1)
  return (
    <Stack>
      {b.winner && (
        <Paper p="lg" ta="center">
          <Text className="section-label">{t('bracket.winner')}</Text>
          <Group gap="xs" justify="center" mt={4}>
            <TrophyIcon size={26} color="var(--mantine-color-yellow-filled)" />
            <Text fw={800} fz={24} c="teal">
              {b.winner.name}
            </Text>
          </Group>
        </Paper>
      )}
      <ScrollArea type="auto">
        <Group align="stretch" wrap="nowrap" gap="md">
          {rounds.map((r) => {
            const [key, values] = roundLabel(r, b.rounds)
            return (
              <Stack key={r} gap="xs" justify="space-around">
                <h2 className="section-label" style={{ margin: 0 }}>
                  {t(key, values)}
                </h2>
                {b.matches.filter((bm) => bm.round === r).map((bm) => <MatchCard key={bm.match.id} bm={bm} />)}
              </Stack>
            )
          })}
        </Group>
      </ScrollArea>
    </Stack>
  )
}
