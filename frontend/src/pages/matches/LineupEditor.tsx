import { Button, Card, Checkbox, Group, Stack, Text } from '@mantine/core'
import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { api, call, type Match } from '../../api/client'
import PlayerPicker from '../../components/PlayerPicker'

type Entry = { playerId: string; side: 'HOME' | 'AWAY'; inGoal: boolean }

function useRoster(teamId: string | null) {
  return (
    useQuery({
      queryKey: ['team', teamId],
      queryFn: () => call(api.GET('/api/teams/{id}', { params: { path: { id: teamId! } } })),
      enabled: teamId !== null,
    }).data?.players ?? []
  )
}

/** Picks the players of the sides I may pick (a player can't be on both) and who is in goal; Save replaces those sides. */
export default function LineupEditor({ match, onSave, disabled }: { match: Match; onSave: (entries: Entry[]) => void; disabled: boolean }) {
  const { t } = useTranslation()
  const canPick = (side: Entry['side']) => match.lineupSides.includes(side)
  // an empty side starts from the roster players who said they're in
  const ids = (side: Entry['side']) => {
    const picked = match.lineup.filter((l) => l.side === side).map((l) => l.playerId)
    return picked.length ? picked : match.rsvps.filter((r) => r.side === side && r.going).map((r) => r.playerId)
  }
  const [home, setHome] = useState(() => ids('HOME'))
  const [away, setAway] = useState(() => ids('AWAY'))
  const [keepers, setKeepers] = useState(() => new Set(match.lineup.filter((l) => l.inGoal).map((l) => l.playerId)))
  const mine = useQuery({ queryKey: ['players', 'mine'], queryFn: () => call(api.GET('/api/players/mine')) })
  // a competition side's roster comes first; players already in the lineup always have names
  const homeRoster = useRoster(match.competitionId ? match.home.teamId : null)
  const awayRoster = useRoster(match.competitionId ? match.away.teamId : null)
  const first = (side: Entry['side'], players: { id: string; name: string }[]) => [
    ...players,
    ...match.lineup.filter((l) => l.side === side).map((l) => ({ id: l.playerId, name: l.name })),
    ...match.rsvps.filter((r) => r.side === side && r.going).map((r) => ({ id: r.playerId, name: r.name })),
  ]
  const names = new Map([
    ...(mine.data ?? []).map((p) => [p.id, p.name] as const),
    ...[...homeRoster, ...awayRoster].map((p) => [p.id, p.name] as const),
    ...match.rsvps.map((r) => [r.playerId, r.name] as const),
    ...match.lineup.map((l) => [l.playerId, l.name] as const),
  ])

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
    <Card>
      <Stack gap="lg">
        {/* a saved lineup opens from the match page's Lineup row, which already names it */}
        {!match.lineup.some((l) => canPick(l.side)) && (
          <Text fw={700} size="lg">
            {t('match.lineup')}
          </Text>
        )}
        {canPick('HOME') && (
          <Stack gap="xs" className="section-label">
            <PlayerPicker label={match.home.name ?? undefined} value={home} onChange={setHome} excludeIds={away} first={first('HOME', homeRoster)} />
            {keeperBoxes(home)}
          </Stack>
        )}
        {canPick('AWAY') && (
          <Stack gap="xs" className="section-label">
            <PlayerPicker label={match.away.name ?? undefined} value={away} onChange={setAway} excludeIds={home} first={first('AWAY', awayRoster)} />
            {keeperBoxes(away)}
          </Stack>
        )}
        <Group>
          <Button
            disabled={disabled}
            onClick={() =>
              onSave([
                ...(canPick('HOME') ? home : []).map((playerId) => ({ playerId, side: 'HOME' as const, inGoal: keepers.has(playerId) })),
                ...(canPick('AWAY') ? away : []).map((playerId) => ({ playerId, side: 'AWAY' as const, inGoal: keepers.has(playerId) })),
              ])
            }
          >
            {t('match.saveLineup')}
          </Button>
        </Group>
      </Stack>
    </Card>
  )
}
