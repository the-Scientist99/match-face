import { ActionIcon, Group, Stack, Text } from '@mantine/core'
import { useTranslation } from 'react-i18next'
import type { Match, MatchEvent } from '../../api/client'

const ICON: Record<MatchEvent['type'], string> = { GOAL: '⚽', OWN_GOAL: '⚽', YELLOW: '🟨', RED: '🟥', MOTM: '⭐' }

/** Events in match order; {@code onDelete} adds an undo button to each. */
export default function EventList({ match, onDelete, disabled }: { match: Match; onDelete?: (id: string) => void; disabled?: boolean }) {
  const { t } = useTranslation()
  if (!match.events.length) return <Text c="dimmed">{t('event.none')}</Text>
  return (
    <Stack gap={4}>
      {match.events.map((e) => (
        <Group key={e.id} gap="xs" justify={e.side === 'HOME' ? 'flex-start' : 'flex-end'} wrap="nowrap">
          <Text size="sm">
            {e.minute !== null && `${e.minute}' `}
            {ICON[e.type]} {e.playerName ?? t('event.unknownScorer')}
            {e.type === 'OWN_GOAL' && ` (${t('event.type.OWN_GOAL')})`}
            {e.assistPlayerName && ` · ${t('event.assistBy', { name: e.assistPlayerName })}`}
          </Text>
          {onDelete && (
            <ActionIcon variant="subtle" color="red" size="sm" aria-label={t('event.undo')} disabled={disabled} onClick={() => onDelete(e.id)}>
              ✕
            </ActionIcon>
          )}
        </Group>
      ))}
    </Stack>
  )
}
