import createClient from 'openapi-fetch'
import type { components, paths } from './schema'

/** springdoc marks no response field as required, but the backend always sends them all. */
export type Sent<T> = T extends (infer U)[] ? Sent<U>[] : T extends object ? { [K in keyof T]-?: Sent<T[K]> } : T
type Schema<K extends keyof components['schemas']> = Sent<components['schemas'][K]>

export type Me = Schema<'MeResponse'>
export type AuthResponse = Schema<'AuthResponse'>
export type Player = Schema<'PlayerResponse'>
export type Team = Schema<'TeamResponse'>
export type TeamSummary = Schema<'TeamSummary'>

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
