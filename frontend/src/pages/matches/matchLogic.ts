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

/** Events and finishing wait for kickoff; no kickoff (older matches, competition fixtures) counts as started. */
export function hasStarted(kickoffAt: string | null, now = new Date()): boolean {
  return !kickoffAt || new Date(kickoffAt) <= now
}

/** In play: past a real kickoff but not finished. A fixture with no date counts as started, but nobody can say it's on. */
export function isLive(m: { status: string; kickoffAt: string | null }, now = new Date()): boolean {
  return m.status === 'SCHEDULED' && !!m.kickoffAt && hasStarted(m.kickoffAt, now)
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

/** A knockout match waiting for an earlier round to fill a side: nothing can be entered yet. */
export function teamsUndecided(m: { knockout: boolean; home: { teamId: string | null }; away: { teamId: string | null } }): boolean {
  return m.knockout && (m.home.teamId === null || m.away.teamId === null)
}

/** The pitch's rows of outfield players: at most three across, the biggest rows first. */
export function pitchRows<T>(items: T[]): T[][] {
  const count = Math.ceil(items.length / 3)
  let at = 0
  return Array.from({ length: count }, (_, i) => {
    const size = Math.floor(items.length / count) + (i < items.length % count ? 1 : 0)
    return items.slice((at += size) - size, at)
  })
}

/** The names on the shirts: surnames, an initial when two share one, the whole name when the last word is too short to be one ("Marko P"). */
export function shirtNames(names: string[]): string[] {
  const words = names.map((n) => n.trim().split(/\s+/))
  const short = (w: string[], i: number) => (Array.from(w[w.length - 1]).length >= 3 ? w[w.length - 1] : names[i].trim())
  const shorts = words.map(short)
  return shorts.map((s, i) => (shorts.filter((o) => o === s).length > 1 && words[i].length > 1 ? `${Array.from(words[i][0])[0]}. ${s}` : s))
}

/** A player's result in a finished match, from their side. */
export function resultFor(score: { home: number; away: number }, side: 'HOME' | 'AWAY'): 'won' | 'drawn' | 'lost' {
  const diff = side === 'HOME' ? score.home - score.away : score.away - score.home
  return diff > 0 ? 'won' : diff < 0 ? 'lost' : 'drawn'
}
