import { Badge, Group, Stack, Text, Title } from '@mantine/core'
import { useTranslation } from 'react-i18next'
import type { Match } from '../../api/client'
import { formatKickoff } from '../../components/MatchList'

export default function ScoreHeader({ match }: { match: Match }) {
  const { t, i18n } = useTranslation()
  return (
    <Stack gap={4} align="center">
      <Group wrap="nowrap" justify="center" gap="md" w="100%">
        <Text fw={600} ta="right" flex={1} truncate>
          {match.home.name ?? t('bracket.tbd')}
        </Text>
        <Title order={1}>
          {match.score.home} – {match.score.away}
        </Title>
        <Text fw={600} flex={1} truncate>
          {match.away.name ?? t('bracket.tbd')}
        </Text>
      </Group>
      <Group gap="xs">
        <Badge variant="light" color={match.status === 'FINISHED' ? 'green' : match.status === 'CANCELLED' ? 'gray' : 'blue'}>
          {t(`match.status.${match.status}`)}
        </Badge>
        <Text size="sm" c="dimmed">
          {[formatKickoff(match.kickoffAt, i18n.language), match.venue].filter(Boolean).join(' · ')}
        </Text>
      </Group>
    </Stack>
  )
}
