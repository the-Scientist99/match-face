import { Badge, Group, Paper, Stack, Text } from '@mantine/core'
import { useTranslation } from 'react-i18next'
import type { Match } from '../../api/client'
import Crest, { sideTints, type Tint } from '../../components/Crest'
import { formatKickoff } from '../../components/MatchList'

const STATUS_COLOR = { SCHEDULED: 'blue', FINISHED: 'teal', CANCELLED: 'gray' } as const

export default function ScoreHeader({ match }: { match: Match }) {
  const { t, i18n } = useTranslation()
  const details = [formatKickoff(match.kickoffAt, i18n.language), match.venue].filter(Boolean).join(' · ')
  const tints = sideTints(match.home.name, match.away.name)
  const side = (name: string | null, tint: Tint) => (
    <Stack gap={8} align="center" flex={1} miw={0}>
      <Crest name={name} tint={tint} size={56} />
      <Text component="div" fw={700} size="sm" ta="center" lh={1.25} lineClamp={2} c={name ? undefined : 'dimmed'}>
        {name ?? t('bracket.tbd')}
      </Text>
    </Stack>
  )
  return (
    <Paper p="lg" pt="xl">
      <Stack gap="lg" align="center">
        <Group wrap="nowrap" align="flex-start" gap="xs" w="100%">
          {side(match.home.name, tints.HOME)}
          <Text className="score score-big" component="div" aria-live="polite">
            {match.score.home}
            <span className="score-sep">–</span>
            {match.score.away}
          </Text>
          {side(match.away.name, tints.AWAY)}
        </Group>
        <Group gap="xs" justify="center">
          <Badge color={STATUS_COLOR[match.status]}>{t(`match.status.${match.status}`)}</Badge>
          {details && (
            <Text size="sm" c="dimmed" fw={500}>
              {details}
            </Text>
          )}
        </Group>
      </Stack>
    </Paper>
  )
}
