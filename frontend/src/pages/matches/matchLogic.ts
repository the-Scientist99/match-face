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
