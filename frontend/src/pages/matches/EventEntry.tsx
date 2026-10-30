import { Button, Card, Group, Modal, NumberInput, Select, SimpleGrid, Stack, Text } from '@mantine/core'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { Match } from '../../api/client'
import { sideTints } from '../../components/Crest'
import { CardIcon } from '../../components/icons'

type Side = 'HOME' | 'AWAY'
type EventType = 'GOAL' | 'OWN_GOAL' | 'YELLOW' | 'RED' | 'MOTM'
export type NewEvent = { type: EventType; side: Side; playerId?: string; assistPlayerId?: string; minute?: number }

const OTHER_TYPES = ['YELLOW', 'RED', 'OWN_GOAL', 'MOTM'] as const

/** Tap a player, pick what happened. A goal then asks for an optional assist and minute. */
export default function EventEntry({ match, onAdd, disabled }: { match: Match; onAdd: (e: NewEvent) => void; disabled: boolean }) {
  const { t } = useTranslation()
  const [picked, setPicked] = useState<{ side: Side; playerId: string; name: string } | null>(null)
  const [goal, setGoal] = useState(false)
  const [assist, setAssist] = useState<string | null>(null)
  const [minute, setMinute] = useState<number | string>('')

  const pick = (side: Side, playerId: string, name: string) => {
    setPicked({ side, playerId, name })
    setGoal(false)
    setAssist(null)
    setMinute('')
  }
  const saveGoal = (withDetails: boolean) => {
    if (!picked) return
    onAdd({
      type: 'GOAL',
      side: picked.side,
      playerId: picked.playerId,
      ...(withDetails && assist ? { assistPlayerId: assist } : {}),
      ...(withDetails && typeof minute === 'number' ? { minute } : {}),
    })
    setPicked(null)
  }

  const tints = sideTints(match.home.name, match.away.name)
  const column = (side: Side) => (
    <Stack gap={8} data-tint={tints[side]}>
      <Group gap={8} wrap="nowrap" px={4}>
        <span className="tint-dot" />
        <Text className="section-label" truncate>
          {side === 'HOME' ? match.home.name : match.away.name}
        </Text>
      </Group>
      {match.lineup
        .filter((l) => l.side === side)
        .map((l) => (
          <Button
            key={l.playerId}
            h="auto"
            mih={52}
            px={12}
            py={4}
            justify="flex-start"
            fullWidth
            disabled={disabled}
            className="pressable side-button"
            styles={{ label: { flexDirection: 'column', alignItems: 'flex-start', whiteSpace: 'normal', textAlign: 'start', lineHeight: 1.25 } }}
            onClick={() => pick(side, l.playerId, l.name)}
          >
            {l.name}
            {l.inGoal && (
              <Text size="xs" fw={500} c="dimmed">
                {t('match.keeper')}
              </Text>
            )}
          </Button>
        ))}
      <Button
        variant="default"
        size="sm"
        h="auto"
        mih={44}
        py={6}
        fullWidth
        styles={{ label: { whiteSpace: 'normal', lineHeight: 1.25 } }}
        className="pressable dashed-button"
        disabled={disabled}
        onClick={() => onAdd({ type: 'GOAL', side })}
      >
        {t('event.unknownGoal')}
      </Button>
    </Stack>
  )

  const assistOptions = picked
    ? match.lineup.filter((l) => l.side === picked.side && l.playerId !== picked.playerId).map((l) => ({ value: l.playerId, label: l.name }))
    : []

  return (
    <>
      <Card>
        <h2 className="section-title" style={{ marginBottom: 'var(--mantine-spacing-md)' }}>
          {t('event.tapPlayer')}
        </h2>
        <SimpleGrid cols={2} spacing="sm">
          {column('HOME')}
          {column('AWAY')}
        </SimpleGrid>
      </Card>
      <Modal className="sheet" opened={picked !== null} onClose={() => setPicked(null)} title={picked?.name}>
        {goal ? (
          <Stack>
            <Select label={t('event.assist')} data={assistOptions} value={assist} onChange={setAssist} clearable comboboxProps={{ withinPortal: false }} />
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
        ) : (
          <Stack gap="xs">
            <Button size="lg" fullWidth disabled={disabled} onClick={() => setGoal(true)}>
              {t('event.type.GOAL')}
            </Button>
            <SimpleGrid cols={2} spacing="xs">
              {OTHER_TYPES.map((type) => (
                <Button
                  key={type}
                  variant="default"
                  h="auto"
                  mih={52}
                  px={8}
                  disabled={disabled}
                  styles={{ label: { whiteSpace: 'normal', lineHeight: 1.25 } }}
                  leftSection={type === 'YELLOW' ? <CardIcon color="yellow" /> : type === 'RED' ? <CardIcon color="red" /> : undefined}
                  onClick={() => {
                    if (picked) onAdd({ type, side: picked.side, playerId: picked.playerId })
                    setPicked(null)
                  }}
                >
                  {t(`event.type.${type}`)}
                </Button>
              ))}
            </SimpleGrid>
          </Stack>
        )}
      </Modal>
    </>
  )
}
