import type { MatchSummary } from '../../api/client'

type Listed = Pick<MatchSummary, 'id' | 'status' | 'kickoffAt'>

/** Home page sections: upcoming soonest first (no kickoff last); recent keeps the API's newest-first order. */
export function splitMatches<T extends Listed>(matches: T[]): { upcoming: T[]; recent: T[] } {
  const upcoming = matches
    .filter((m) => m.status === 'SCHEDULED')
    .sort((a, b) => (a.kickoffAt ?? '￿').localeCompare(b.kickoffAt ?? '￿'))
  return { upcoming, recent: matches.filter((m) => m.status !== 'SCHEDULED') }
}

/** `<input type="datetime-local">` value (local time) → ISO instant; empty → undefined. */
export function localToIso(local: string): string | undefined {
  return local ? new Date(local).toISOString() : undefined
}

/** ISO instant → `<input type="datetime-local">` value in local time. */
export function isoToLocal(iso: string | null): string {
  if (!iso) return ''
  const d = new Date(iso)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
}

/** The picker's "+ Add guest" option: only for a non-blank search that isn't already a player's name. */
export function showAddGuest(search: string, names: string[]): boolean {
  const s = search.trim().toLowerCase()
  return s !== '' && !names.some((n) => n.toLowerCase() === s)
}

type Named = { id: string; name: string }

/**
 * Picker options: {@code first} (e.g. a side's roster, or players already in the lineup) before my players,
 * each player once. Excluded players (e.g. on the other side) are left out unless already selected.
 */
export function pickerOptions({ first, mine, excludeIds, value }: { first: Named[]; mine: Named[]; excludeIds: string[]; value: string[] }) {
  const seen = new Set<string>()
  return [...first, ...mine]
    .filter((p) => !seen.has(p.id) && seen.add(p.id) && (!excludeIds.includes(p.id) || value.includes(p.id)))
    .map((p) => ({ value: p.id, label: p.name }))
}

/** Fixtures by round, rounds ascending (no round last), API order kept inside each. */
export function groupByRound<T extends { round: number | null }>(matches: T[]): [number | null, T[]][] {
  const groups = new Map<number | null, T[]>()
  for (const m of matches) groups.set(m.round, [...(groups.get(m.round) ?? []), m])
  return [...groups.entries()].sort(([a], [b]) => (a ?? Infinity) - (b ?? Infinity))
}

/** i18n key and values for a bracket round's label: the last three rounds by name, earlier ones by number. */
export function roundLabel(round: number, rounds: number): [string, Record<string, number>] {
  const fromEnd = rounds - round
  if (fromEnd === 0) return ['bracket.final', {}]
  if (fromEnd === 1) return ['bracket.semifinal', {}]
  if (fromEnd === 2) return ['bracket.quarterfinal', {}]
  return ['bracket.round', { round }]
}

/** A cup match can't end level: finishing it needs the penalty-shootout winner. */
export function needsPenalties(m: { knockout: boolean; score: { home: number; away: number } }): boolean {
  return m.knockout && m.score.home === m.score.away
}
