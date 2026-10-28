import { Badge, Group, Paper, Stack, Text } from '@mantine/core'
import { useTranslation } from 'react-i18next'
import type { Match } from '../../api/client'
import { formatKickoff } from '../../components/MatchList'

export default function ScoreHeader({ match }: { match: Match }) {
  const { t, i18n } = useTranslation()
  const details = [formatKickoff(match.kickoffAt, i18n.language), match.venue].filter(Boolean).join(' · ')
  return (
    <Paper withBorder p="lg">
      <Stack gap="sm" align="center">
        <Group wrap="nowrap" justify="center" gap="md" w="100%">
          <Text fw={700} fz="lg" ta="right" flex={1} lh={1.2} lineClamp={2}>
            {match.home.name ?? t('bracket.tbd')}
          </Text>
          <Text className="score score-big" component="div" aria-live="polite">
            {match.score.home}–{match.score.away}
          </Text>
          <Text fw={700} fz="lg" flex={1} lh={1.2} lineClamp={2}>
            {match.away.name ?? t('bracket.tbd')}
          </Text>
        </Group>
        <Group gap="xs" justify="center">
          <Badge color={match.status === 'FINISHED' ? 'teal' : match.status === 'CANCELLED' ? 'gray' : 'blue'}>
            {t(`match.status.${match.status}`)}
          </Badge>
          {details && (
            <Text size="sm" c="dimmed">
              {details}
            </Text>
          )}
        </Group>
      </Stack>
    </Paper>
  )
}
