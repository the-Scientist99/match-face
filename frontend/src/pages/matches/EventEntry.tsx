import { Button, Card, Group, Menu, Modal, NumberInput, Select, SimpleGrid, Stack, Text, VisuallyHidden } from '@mantine/core'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { Match } from '../../api/client'
import { sideTints } from '../../components/Crest'
import { KeeperIcon } from '../../components/icons'

type Side = 'HOME' | 'AWAY'
type EventType = 'GOAL' | 'OWN_GOAL' | 'YELLOW' | 'RED' | 'MOTM'
export type NewEvent = { type: EventType; side: Side; playerId?: string; assistPlayerId?: string; minute?: number }

const TYPES: EventType[] = ['GOAL', 'OWN_GOAL', 'YELLOW', 'RED', 'MOTM']

/** Tap a player, pick what happened. A goal then asks for an optional assist and minute. */
export default function EventEntry({ match, onAdd, disabled }: { match: Match; onAdd: (e: NewEvent) => void; disabled: boolean }) {
  const { t } = useTranslation()
  const [goal, setGoal] = useState<{ side: Side; playerId: string } | null>(null)
  const [assist, setAssist] = useState<string | null>(null)
  const [minute, setMinute] = useState<number | string>('')

  const openGoal = (side: Side, playerId: string) => {
    setGoal({ side, playerId })
    setAssist(null)
    setMinute('')
  }
  const saveGoal = (withDetails: boolean) => {
    if (!goal) return
    onAdd({
      type: 'GOAL',
      ...goal,
      ...(withDetails && assist ? { assistPlayerId: assist } : {}),
      ...(withDetails && typeof minute === 'number' ? { minute } : {}),
    })
    setGoal(null)
  }

  const tints = sideTints(match.home.name, match.away.name)
  // each side's buttons wear that team's colour, the same as its crest in the score header
  const column = (side: Side) => (
    <Stack gap={8} data-tint={tints[side]}>
      <Group gap={8} wrap="nowrap" px={4}>
        <span className="tint-dot" />
        <Text fw={700} size="sm" truncate>
          {side === 'HOME' ? match.home.name : match.away.name}
        </Text>
      </Group>
      {match.lineup
        .filter((l) => l.side === side)
        .map((l) => (
          <Menu key={l.playerId} position="bottom-start" disabled={disabled}>
            <Menu.Target>
              <Button
                variant="light"
                size="md"
                h={48}
                px={l.inGoal ? 10 : 12}
                justify="flex-start"
                fullWidth
                disabled={disabled}
                className="pressable side-button"
                leftSection={
                  l.inGoal ? (
                    <span className="keeper-badge">
                      <KeeperIcon size={14} />
                    </span>
                  ) : undefined
                }
              >
                {l.name}
                {l.inGoal && <VisuallyHidden> ({t('match.keeper')})</VisuallyHidden>}
              </Button>
            </Menu.Target>
            <Menu.Dropdown>
              {TYPES.map((type) => (
                <Menu.Item
                  key={type}
                  onClick={() => (type === 'GOAL' ? openGoal(side, l.playerId) : onAdd({ type, side, playerId: l.playerId }))}
                >
                  {t(`event.type.${type}`)}
                </Menu.Item>
              ))}
            </Menu.Dropdown>
          </Menu>
        ))}
      <Button
        variant="default"
        size="sm"
        h="auto"
        mih={44}
        py={6}
        fullWidth
        styles={{ label: { whiteSpace: 'normal', lineHeight: 1.25 } }}
        className="pressable dashed-button" disabled={disabled} onClick={() => onAdd({ type: 'GOAL', side })}>
        {t('event.unknownGoal')}
      </Button>
    </Stack>
  )

  const assistOptions = goal
    ? match.lineup.filter((l) => l.side === goal.side && l.playerId !== goal.playerId).map((l) => ({ value: l.playerId, label: l.name }))
    : []

  return (
    <>
      <Card>
        <Text component="h2" className="section-label" m={0} mb="md">
          {t('event.tapPlayer')}
        </Text>
        <SimpleGrid cols={2} spacing="sm">
          {column('HOME')}
          {column('AWAY')}
        </SimpleGrid>
      </Card>
      <Modal opened={goal !== null} onClose={() => setGoal(null)} title={t('event.type.GOAL')} centered>
        <Stack>
          <Select label={t('event.assist')} data={assistOptions} value={assist} onChange={setAssist} clearable />
          <NumberInput label={t('event.minute')} min={0} max={150} allowDecimal={false} value={minute} onChange={setMinute} />
          <Group justify="flex-end">
            <Button variant="default" disabled={disabled} onClick={() => saveGoal(false)}>
              {t('event.skip')}
            </Button>
            <Button disabled={disabled} onClick={() => saveGoal(true)}>
              {t('common.save')}
            </Button>
          </Group>
        </Stack>
      </Modal>
    </>
  )
}
