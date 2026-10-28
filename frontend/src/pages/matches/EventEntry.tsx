import { Button, Group, Menu, Modal, NumberInput, Select, SimpleGrid, Stack, Text } from '@mantine/core'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { Match } from '../../api/client'

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

  const column = (side: Side) => (
    <Stack gap="xs">
      <Text fw={600} truncate>
        {side === 'HOME' ? match.home.name : match.away.name}
      </Text>
      {match.lineup
        .filter((l) => l.side === side)
        .map((l) => (
          <Menu key={l.playerId} position="bottom-start" disabled={disabled}>
            <Menu.Target>
              <Button variant="light" size="sm" justify="flex-start" disabled={disabled}>
                {l.inGoal ? '🧤 ' : ''}
                {l.name}
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
      <Button variant="subtle" size="xs" disabled={disabled} onClick={() => onAdd({ type: 'GOAL', side })}>
        {t('event.unknownGoal')}
      </Button>
    </Stack>
  )

  const assistOptions = goal
    ? match.lineup.filter((l) => l.side === goal.side && l.playerId !== goal.playerId).map((l) => ({ value: l.playerId, label: l.name }))
    : []

  return (
    <>
      <SimpleGrid cols={2}>
        {column('HOME')}
        {column('AWAY')}
      </SimpleGrid>
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
