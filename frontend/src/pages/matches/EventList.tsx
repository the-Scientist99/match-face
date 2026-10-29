import { ActionIcon, Button, Card, Group, Stack, Text } from '@mantine/core'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { Match, MatchEvent } from '../../api/client'
import { BallIcon, CardIcon, CloseIcon, StarIcon } from '../../components/icons'

function EventIcon({ type, label }: { type: MatchEvent['type']; label: string }) {
  if (type === 'YELLOW' || type === 'RED') return <CardIcon color={type === 'YELLOW' ? 'yellow' : 'red'} label={label} />
  const a11y = { role: 'img', 'aria-label': label, 'aria-hidden': false } as const
  if (type === 'MOTM') return <StarIcon className="icon-star" {...a11y} />
  return <BallIcon className={type === 'OWN_GOAL' ? 'icon-own-goal' : undefined} {...a11y} />
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
        <Text component="h2" className="section-label" m={0}>
          {t('event.title')}
        </Text>
        {canEdit && (
          <Button variant="subtle" size="compact-md" mr={-8} h={44} onClick={() => setEditing(!editing)}>
            {editing ? t('event.done') : t('event.edit')}
          </Button>
        )}
      </Group>
      {match.events.length ? (
        <div className="timeline">
          {match.events.map((e) => {
            const home = e.side === 'HOME'
            const cell = (
              // home reads right to left, so its icon sits by the minute and the remove button on the outside
              <Group gap="xs" wrap="nowrap" style={{ flexDirection: home ? 'row-reverse' : 'row' }}>
                <EventIcon type={e.type} label={t(`event.type.${e.type}`)} />
                <Stack gap={0} miw={0} flex={1} ta={home ? 'right' : 'left'}>
                  <Text size="sm" fw={600} lh={1.3}>
                    {e.playerName ?? t('event.unknownScorer')}
                    {e.type === 'OWN_GOAL' && ` (${t('event.type.OWN_GOAL')})`}
                  </Text>
                  {e.assistPlayerName && (
                    <Text size="xs" c="dimmed">
                      {t('event.assistBy', { name: e.assistPlayerName })}
                    </Text>
                  )}
                </Stack>
                {canEdit && editing && (
                  <ActionIcon variant="subtle" color="gray" radius="xl" size="xl" aria-label={t('event.undo')} disabled={disabled} onClick={() => onDelete(e.id)}>
                    <CloseIcon size={18} />
                  </ActionIcon>
                )}
              </Group>
            )
            return (
              <div key={e.id} className="tl-row">
                <div>{home && cell}</div>
                {e.minute !== null ? <span className="tl-minute score">{e.minute}′</span> : <span className="tl-dot" />}
                <div>{!home && cell}</div>
              </div>
            )
          })}
        </div>
      ) : (
        <Text c="dimmed" ta="center" size="sm" pb="xs">
          {t('event.none')}
        </Text>
      )}
    </Card>
  )
}
