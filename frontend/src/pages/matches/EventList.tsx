import { ActionIcon, Card, Group, Stack, Text } from '@mantine/core'
import { useTranslation } from 'react-i18next'
import type { Match, MatchEvent } from '../../api/client'

const ICON: Record<MatchEvent['type'], string> = { GOAL: '⚽', OWN_GOAL: '⚽', YELLOW: '🟨', RED: '🟥', MOTM: '⭐' }
const TINT: Record<MatchEvent['type'], string | undefined> = { GOAL: undefined, OWN_GOAL: 'red', YELLOW: 'yellow', RED: 'red', MOTM: 'star' }

/** Events in match order; {@code onDelete} adds an undo button to each. Home events sit left, away right. */
export default function EventList({ match, onDelete, disabled }: { match: Match; onDelete?: (id: string) => void; disabled?: boolean }) {
  const { t } = useTranslation()
  if (!match.events.length) {
    return (
      <Card>
        <Text c="dimmed" ta="center" size="sm">
          {t('event.none')}
        </Text>
      </Card>
    )
  }
  return (
    <Card padding="md">
      <Stack gap="xs">
        {match.events.map((e) => {
          const home = e.side === 'HOME'
          const badge = (
            <span className="emoji-badge" data-tint={TINT[e.type]} aria-hidden>
              {ICON[e.type]}
            </span>
          )
          const undo = onDelete && (
            <ActionIcon variant="subtle" color="gray" radius="xl" size="lg" aria-label={t('event.undo')} disabled={disabled} onClick={() => onDelete(e.id)}>
              ✕
            </ActionIcon>
          )
          return (
            <Group key={e.id} gap="sm" wrap="nowrap" className="event-row" style={{ flexDirection: home ? 'row' : 'row-reverse' }}>
              {badge}
              <Text size="sm" fw={500} flex={1} ta={home ? 'left' : 'right'}>
                {e.minute !== null && (
                  <Text span c="dimmed" fw={600} mr={4}>
                    {e.minute}'
                  </Text>
                )}
                {e.playerName ?? t('event.unknownScorer')}
                {e.type === 'OWN_GOAL' && ` (${t('event.type.OWN_GOAL')})`}
                {e.assistPlayerName && (
                  <Text span c="dimmed" fw={400}>
                    {` · ${t('event.assistBy', { name: e.assistPlayerName })}`}
                  </Text>
                )}
              </Text>
              {undo}
            </Group>
          )
        })}
      </Stack>
    </Card>
  )
}
