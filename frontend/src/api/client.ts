import createClient from 'openapi-fetch'
import type { components, paths } from './schema'

/** springdoc marks no response field as required, but the backend always sends them all. */
export type Sent<T> = T extends (infer U)[] ? Sent<U>[] : T extends object ? { [K in keyof T]-?: Sent<T[K]> } : T
type Schema<K extends keyof components['schemas']> = Sent<components['schemas'][K]>

export type Me = WithNull<Schema<'MeResponse'>, 'imageUrl'>
export type AuthResponse = Schema<'AuthResponse'>
export type Player = WithNull<Schema<'PlayerResponse'>, 'imageUrl'>
export type RosterPlayer = WithNull<Schema<'RosterPlayer'>, 'imageUrl'>
export type Team = WithNull<Omit<Schema<'TeamResponse'>, 'players'>, 'imageUrl'> & { players: RosterPlayer[] }
export type TeamSummary = WithNull<Schema<'TeamSummary'>, 'imageUrl'>

/** `K` may be null in responses (the backend sends null for "none"). */
type WithNull<T, K extends keyof T> = Omit<T, K> & { [P in K]: T[P] | null }

/** `name` is null for a cup slot an earlier round hasn't filled yet. */
export type SideView = WithNull<Schema<'SideView'>, 'teamId' | 'name' | 'imageUrl'>
export type MatchEvent = WithNull<Schema<'EventView'>, 'playerId' | 'playerName' | 'assistPlayerId' | 'assistPlayerName' | 'minute'>
export type Match = WithNull<
  Omit<Schema<'MatchResponse'>, 'home' | 'away' | 'events'>,
  'kickoffAt' | 'venue' | 'visibility' | 'competitionId' | 'competitionName' | 'round' | 'penaltyWinner' | 'mySide' | 'maxPerSide' | 'myRsvp'
> & { home: SideView; away: SideView; events: MatchEvent[] }
export type MatchSummary = WithNull<
  Schema<'MatchSummary'>,
  'kickoffAt' | 'competitionId' | 'competitionName' | 'round' | 'homeName' | 'awayName' | 'homeImageUrl' | 'awayImageUrl' | 'spotsLeft'
>
export type CompetitionTeamView = WithNull<Schema<'CompetitionTeamView'>, 'seed'>
export type Competition = WithNull<Omit<Schema<'CompetitionResponse'>, 'teams'>, 'imageUrl'> & { teams: CompetitionTeamView[] }
export type CompetitionSummary = WithNull<Schema<'CompetitionSummary'>, 'imageUrl'>
export type OpenCompetitions = Omit<Schema<'OpenCompetitions'>, 'items'> & { items: CompetitionSummary[] }
export type StandingRow = Schema<'StandingRow'>
export type CompetitionStats = Schema<'CompetitionStatsResponse'>
export type TeamRef = Schema<'TeamRef'>
export type BracketMatch = WithNull<Omit<Schema<'BracketMatch'>, 'match'>, 'penaltyWinner'> & { match: MatchSummary }
export type Bracket = { rounds: number; matches: BracketMatch[]; winner: TeamRef | null }
export type PlayerMatchRow = Omit<Schema<'PlayerMatchRow'>, 'match'> & { match: MatchSummary }
export type PlayerProfile = WithNull<Omit<Schema<'PlayerProfileResponse'>, 'matches'>, 'imageUrl'> & { matches: PlayerMatchRow[] }

/** Unwraps an openapi-fetch result: the data, or the error body thrown (for errors.ts to read). */
export async function call<T>(result: Promise<{ data?: T; error?: unknown }>): Promise<Sent<T>> {
  const { data, error } = await result
  if (error !== undefined) throw error
  return data as Sent<T>
}

let accessToken: string | null = null
/** `offline` = the refresh request itself failed; the session may still be valid. */
let refreshing: Promise<AuthResponse | null | 'offline'> | null = null
let sessionExpired: (() => void) | null = null

export function setAccessToken(token: string | null) {
  accessToken = token
}

/** Registers the one handler for "refresh failed, the user is logged out". Returns an unsubscribe. */
export function onSessionExpired(cb: () => void): () => void {
  sessionExpired = cb
  return () => {
    if (sessionExpired === cb) sessionExpired = null
  }
}

/**
 * Swaps the refresh cookie for a new access token. Concurrent callers share one request.
 * Resolves to `null` when the session is over, `'offline'` when the server couldn't be reached.
 */
export function refreshAccessToken(): Promise<AuthResponse | null | 'offline'> {
  refreshing ??= (async () => {
    try {
      const res = await fetch('/api/auth/refresh', { method: 'POST', credentials: 'include' })
      const body = res.ok ? ((await res.json()) as AuthResponse) : null
      setAccessToken(body?.accessToken ?? null)
      return body
    } catch {
      return 'offline' as const
    } finally {
      refreshing = null
    }
  })()
  return refreshing
}

function withToken(req: Request): Request {
  const r = new Request(req)
  if (accessToken) r.headers.set('Authorization', `Bearer ${accessToken}`)
  return r
}

/** fetch that sends the access token and, on a 401, refreshes it once and retries. */
export async function authFetch(req: Request): Promise<Response> {
  const retry = req.clone() // the body can only be read once
  const res = await fetch(withToken(req))
  if (res.status !== 401 || new URL(req.url).pathname.startsWith('/api/auth/')) return res
  const refreshed = await refreshAccessToken()
  if (refreshed === 'offline') return res
  if (!refreshed) {
    sessionExpired?.()
    return res
  }
  return fetch(withToken(retry))
}

export const api = createClient<paths>({ fetch: authFetch, credentials: 'include' })
