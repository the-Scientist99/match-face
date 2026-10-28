import { Badge, Group, Paper, Stack, Text } from '@mantine/core'
import { useTranslation } from 'react-i18next'
import type { Match } from '../../api/client'
import { formatKickoff } from '../../components/MatchList'

const STATUS_COLOR = { SCHEDULED: 'blue', FINISHED: 'teal', CANCELLED: 'gray' } as const

export default function ScoreHeader({ match }: { match: Match }) {
  const { t, i18n } = useTranslation()
  const details = [formatKickoff(match.kickoffAt, i18n.language), match.venue].filter(Boolean).join(' · ')
  const side = (name: string | null) => (
    <Text component="div" fw={700} size="sm" ta="center" lh={1.3} lineClamp={2} className="name-pill">
      {name ?? t('bracket.tbd')}
    </Text>
  )
  return (
    <Paper className="band" p="lg" pt={28}>
      <Stack gap="md" align="center">
        <Group wrap="nowrap" justify="center" gap="md" w="100%">
          <Group flex={1} justify="flex-end">
            {side(match.home.name)}
          </Group>
          <Text className="score score-big" component="div" aria-live="polite">
            {match.score.home}–{match.score.away}
          </Text>
          <Group flex={1} justify="flex-start">
            {side(match.away.name)}
          </Group>
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
