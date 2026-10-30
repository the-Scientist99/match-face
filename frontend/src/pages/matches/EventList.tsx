import { ActionIcon, Button, Card, Group, Stack, Text } from '@mantine/core'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { Match, MatchEvent } from '../../api/client'
import { BallIcon, CardIcon, CloseIcon, StarIcon } from '../../components/icons'

/** Decorative: each event's type is written out beside it. */
function EventMark({ type }: { type: MatchEvent['type'] }) {
  const { t } = useTranslation()
  if (type === 'YELLOW' || type === 'RED') return <CardIcon color={type === 'YELLOW' ? 'yellow' : 'red'} />
  if (type === 'OWN_GOAL') return (
    <span className="event-mark own-goal-mark" aria-hidden>
      {t('event.mark.OWN_GOAL')}
    </span>
  )
  return type === 'MOTM' ? <StarIcon className="icon-star" /> : <BallIcon />
}

/**
 * Events in match order on a timeline: minutes down the middle, home events left, away right.
 * {@code onDelete} adds an Edit toggle that shows a remove button on each event.
 */
export default function EventList({ match, onDelete, disabled }: { match: Match; onDelete?: (id: string) => void; disabled?: boolean }) {
  const { t } = useTranslation()
  const [editing, setEditing] = useState(false)
  const canEdit = !!onDelete && match.events.length > 0
  return (
    <Card padding="md" pt="xs">
      <Group justify="space-between" mih={44}>
        <h2 className="section-title">{t('event.title')}</h2>
        {canEdit && (
          <Button variant="subtle" size="sm" mr={-8} aria-pressed={editing} onClick={() => setEditing(!editing)}>
            {editing ? t('event.done') : t('event.edit')}
          </Button>
        )}
      </Group>
      {match.events.length ? (
        <ol className="timeline">
          {match.events.map((e) => {
            const home = e.side === 'HOME'
            const type = t(`event.type.${e.type}`)
            const name = e.playerName ?? t('event.unknownScorer')
            const cell = (
              // home reads right to left, so its mark sits by the minute and the remove button on the outside
              <Group gap="xs" wrap="nowrap" style={{ flexDirection: home ? 'row-reverse' : 'row' }}>
                <EventMark type={e.type} />
                <Stack gap={0} miw={0} flex={1} ta={home ? 'right' : 'left'}>
                  <Text size="sm" fw={600} lh={1.3}>
                    {name}
                  </Text>
                  <Text size="xs" c="dimmed" lh={1.3}>
                    {[type, e.assistPlayerName && t('event.assistBy', { name: e.assistPlayerName })].filter(Boolean).join(' · ')}
                  </Text>
                </Stack>
                {canEdit && editing && (
                  <ActionIcon variant="subtle" color="gray" radius="xl" size="xl" aria-label={`${t('event.undo')}: ${type}, ${name}`} disabled={disabled} onClick={() => onDelete(e.id)}>
                    <CloseIcon size={18} />
                  </ActionIcon>
                )}
              </Group>
            )
            return (
              <li key={e.id} className="tl-row">
                <div>{home && cell}</div>
                <span className="tl-minute score">{e.minute !== null && `${e.minute}′`}</span>
                <div>{!home && cell}</div>
              </li>
            )
          })}
        </ol>
      ) : (
        <Text c="dimmed" ta="center" size="sm" pb="xs">
          {t('event.none')}
        </Text>
      )}
    </Card>
  )
}
