import { Button, Checkbox, Group, Stack, Text } from '@mantine/core'
import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { api, call, type Match } from '../../api/client'
import PlayerPicker from '../../components/PlayerPicker'

type Entry = { playerId: string; side: 'HOME' | 'AWAY'; inGoal: boolean }

/** Picks each side's players (a player can't be on both) and who is in goal; Save replaces the lineup. */
export default function LineupEditor({ match, onSave, disabled }: { match: Match; onSave: (entries: Entry[]) => void; disabled: boolean }) {
  const { t } = useTranslation()
  const ids = (side: Entry['side']) => match.lineup.filter((l) => l.side === side).map((l) => l.playerId)
  const [home, setHome] = useState(() => ids('HOME'))
  const [away, setAway] = useState(() => ids('AWAY'))
  const [keepers, setKeepers] = useState(() => new Set(match.lineup.filter((l) => l.inGoal).map((l) => l.playerId)))
  const mine = useQuery({ queryKey: ['players', 'mine'], queryFn: () => call(api.GET('/api/players/mine')) })
  const names = new Map([...(mine.data ?? []).map((p) => [p.id, p.name] as const), ...match.lineup.map((l) => [l.playerId, l.name] as const)])

  const keeperBoxes = (selected: string[]) =>
    selected.map((id) => (
      <Checkbox
        key={id}
        size="xs"
        label={t('match.inGoal', { name: names.get(id) ?? '…' })}
        checked={keepers.has(id)}
        onChange={(e) => {
          const next = new Set(keepers)
          if (e.currentTarget.checked) next.add(id)
          else next.delete(id)
          setKeepers(next)
        }}
      />
    ))

  return (
    <Stack>
      <Text fw={600}>{t('match.lineup')}</Text>
      <PlayerPicker label={match.home.name} value={home} onChange={setHome} excludeIds={away} />
      {keeperBoxes(home)}
      <PlayerPicker label={match.away.name} value={away} onChange={setAway} excludeIds={home} />
      {keeperBoxes(away)}
      <Group>
        <Button
          disabled={disabled}
          onClick={() =>
            onSave([
              ...home.map((playerId) => ({ playerId, side: 'HOME' as const, inGoal: keepers.has(playerId) })),
              ...away.map((playerId) => ({ playerId, side: 'AWAY' as const, inGoal: keepers.has(playerId) })),
            ])
          }
        >
          {t('match.saveLineup')}
        </Button>
      </Group>
    </Stack>
  )
}
