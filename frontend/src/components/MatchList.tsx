import { Badge, Card, Group, Stack, Text } from '@mantine/core'
import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import type { MatchSummary } from '../api/client'
import { htmlLang, type Lang } from '../lang'

export function formatKickoff(iso: string | null, lang: string): string | null {
  return iso ? new Date(iso).toLocaleString(htmlLang(lang as Lang), { dateStyle: 'medium', timeStyle: 'short' }) : null
}

/** One card per match: home · score · away, then kickoff and status. Reused by competitions (M5). */
export default function MatchList({ matches, extra }: { matches: MatchSummary[]; extra?: (m: MatchSummary) => ReactNode }) {
  const { t, i18n } = useTranslation()
  return (
    <Stack gap="xs">
      {matches.map((m) => (
        <Card key={m.id} padding="md" radius="lg" className="pressable" component={Link} to={`/matches/${m.id}`}>
          <Group wrap="nowrap" gap="sm">
            <Text fw={600} ta="right" flex={1} truncate>
              {m.homeName ?? t('bracket.tbd')}
            </Text>
            <Text className="score" fw={700} fz="lg" miw={60} ta="center" c={m.status === 'SCHEDULED' ? 'dimmed' : undefined}>
              {m.status === 'SCHEDULED' ? t('match.vs') : `${m.score.home} – ${m.score.away}`}
            </Text>
            <Text fw={600} flex={1} truncate>
              {m.awayName ?? t('bracket.tbd')}
            </Text>
          </Group>
          <Group justify="center" gap="xs" mt={4}>
            <Text size="xs" c="dimmed">
              {formatKickoff(m.kickoffAt, i18n.language) ?? t('match.noKickoff')}
            </Text>
            {m.status !== 'SCHEDULED' && (
              <Badge size="xs" color={m.status === 'CANCELLED' ? 'gray' : 'teal'}>
                {t(`match.status.${m.status}`)}
              </Badge>
            )}
          </Group>
          {extra && (
            <Group justify="center" mt={2}>
              {extra(m)}
            </Group>
          )}
        </Card>
      ))}
    </Stack>
  )
}
