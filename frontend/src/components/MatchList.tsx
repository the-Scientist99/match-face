import { Badge, Card, Group, Stack, Text } from '@mantine/core'
import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import type { MatchSummary } from '../api/client'
import { htmlLang, type Lang } from '../lang'

export function formatKickoff(iso: string | null, lang: string): string | null {
  return iso ? new Date(iso).toLocaleString(htmlLang(lang as Lang), { dateStyle: 'medium', timeStyle: 'short' }) : null
}

/** One card per match: sides, score (once played), kickoff and status. Reused by competitions (M5). */
export default function MatchList({ matches, extra }: { matches: MatchSummary[]; extra?: (m: MatchSummary) => ReactNode }) {
  const { t, i18n } = useTranslation()
  return (
    <Stack gap="xs">
      {matches.map((m) => (
        <Card key={m.id} withBorder padding="sm" component={Link} to={`/matches/${m.id}`}>
          <Group justify="space-between" wrap="nowrap">
            <Text fw={500} truncate>
              {m.homeName} {m.status === 'SCHEDULED' ? t('match.vs') : `${m.score.home} – ${m.score.away}`} {m.awayName}
            </Text>
            {m.status !== 'SCHEDULED' && (
              <Badge size="sm" variant="light" color={m.status === 'CANCELLED' ? 'gray' : 'green'}>
                {t(`match.status.${m.status}`)}
              </Badge>
            )}
          </Group>
          <Text size="sm" c="dimmed">
            {formatKickoff(m.kickoffAt, i18n.language) ?? t('match.noKickoff')}
          </Text>
          {extra?.(m)}
        </Card>
      ))}
    </Stack>
  )
}
