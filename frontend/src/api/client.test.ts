import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { safeNext } from '../auth/RequireAuth'
import { authFetch, call, onSessionExpired, setAccessToken } from './client'

const fetchMock = vi.fn<(input: RequestInfo | URL, init?: RequestInit) => Promise<Response>>()
const me = { id: 'u1', email: 'a@test.dev', locale: 'en', playerId: 'p1', displayName: 'Ana' }

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
const refreshOk = (token: string) => json(200, { accessToken: token, user: me })
const isRefresh = (input: RequestInfo | URL) => String(input instanceof Request ? input.url : input).endsWith('/api/auth/refresh')
const authHeader = (call: number) => (fetchMock.mock.calls[call][0] as Request).headers.get('Authorization')

beforeEach(() => {
  fetchMock.mockReset()
  vi.stubGlobal('fetch', fetchMock)
  setAccessToken(null)
})
afterEach(() => vi.unstubAllGlobals())

test('adds bearer header when a token is set', async () => {
  setAccessToken('t1')
  fetchMock.mockResolvedValueOnce(json(200, {}))
  await authFetch(new Request('http://localhost/api/me'))
  expect(authHeader(0)).toBe('Bearer t1')
})

test('on 401 refreshes once, retries the original request with the new token, returns the retried response', async () => {
  setAccessToken('old')
  fetchMock
    .mockResolvedValueOnce(json(401, { code: 'UNAUTHORIZED' }))
    .mockResolvedValueOnce(refreshOk('new'))
    .mockResolvedValueOnce(json(200, { ok: true }))
  const res = await authFetch(new Request('http://localhost/api/me', { method: 'PATCH', body: '{"locale":"me"}' }))
  expect(res.status).toBe(200)
  expect(await res.json()).toEqual({ ok: true })
  expect(fetchMock).toHaveBeenCalledTimes(3)
  expect(isRefresh(fetchMock.mock.calls[1][0])).toBe(true)
  const retried = fetchMock.mock.calls[2][0] as Request
  expect(retried.headers.get('Authorization')).toBe('Bearer new')
  expect(await retried.text()).toBe('{"locale":"me"}')
})

test('two concurrent 401s share one refresh call', async () => {
  setAccessToken('old')
  fetchMock.mockImplementation(async (input) => {
    if (isRefresh(input)) return refreshOk('new')
    return (input as Request).headers.get('Authorization') === 'Bearer new' ? json(200, {}) : json(401, {})
  })
  const [a, b] = await Promise.all([
    authFetch(new Request('http://localhost/api/a')),
    authFetch(new Request('http://localhost/api/b')),
  ])
  expect([a.status, b.status]).toEqual([200, 200])
  expect(fetchMock.mock.calls.filter(([input]) => isRefresh(input))).toHaveLength(1)
})

test('when refresh fails, returns the original 401, clears the token and calls onSessionExpired', async () => {
  const expired = vi.fn()
  const off = onSessionExpired(expired)
  setAccessToken('old')
  fetchMock
    .mockResolvedValueOnce(json(401, { code: 'UNAUTHORIZED' }))
    .mockResolvedValueOnce(json(401, { code: 'UNAUTHORIZED' }))
    .mockResolvedValueOnce(json(200, {}))
  const res = await authFetch(new Request('http://localhost/api/me'))
  expect(res.status).toBe(401)
  expect(await res.json()).toEqual({ code: 'UNAUTHORIZED' })
  expect(expired).toHaveBeenCalledOnce()
  await authFetch(new Request('http://localhost/api/me'))
  expect(authHeader(2)).toBeNull()
  off()
})

test('a network error during refresh keeps the session: returns the 401 without expiring or clearing the token', async () => {
  const expired = vi.fn()
  const off = onSessionExpired(expired)
  setAccessToken('old')
  fetchMock
    .mockResolvedValueOnce(json(401, { code: 'UNAUTHORIZED' }))
    .mockRejectedValueOnce(new TypeError('Failed to fetch'))
    .mockResolvedValueOnce(json(200, {}))
  const res = await authFetch(new Request('http://localhost/api/me'))
  expect(res.status).toBe(401)
  expect(expired).not.toHaveBeenCalled()
  await authFetch(new Request('http://localhost/api/me'))
  expect(authHeader(2)).toBe('Bearer old')
  off()
})

test('never tries to refresh for /api/auth/* requests', async () => {
  fetchMock.mockResolvedValueOnce(json(401, { code: 'BAD_CREDENTIALS' }))
  const res = await authFetch(new Request('http://localhost/api/auth/login', { method: 'POST' }))
  expect(res.status).toBe(401)
  expect(fetchMock).toHaveBeenCalledTimes(1)
})

test('safeNext keeps "/claim/abc", rejects "//evil.com", "https://evil.com" and null → "/"', () => {
  expect(safeNext('/claim/abc')).toBe('/claim/abc')
  expect(safeNext('//evil.com')).toBe('/')
  expect(safeNext('/\\evil.com')).toBe('/')
  expect(safeNext('https://evil.com')).toBe('/')
  expect(safeNext(null)).toBe('/')
  expect(safeNext('/\t/evil.com')).toBe('/') // URL parsing drops tabs and newlines: "/\t/x" becomes "//x"
  expect(safeNext('/\n/evil.com')).toBe('/')
})

test('call returns data, and throws the error body so errorCode can read it', async () => {
  await expect(call(Promise.resolve({ data: { id: 'x' } }))).resolves.toEqual({ id: 'x' })
  await expect(call(Promise.resolve({ error: { code: 'FORBIDDEN' } }))).rejects.toEqual({ code: 'FORBIDDEN' })
})
