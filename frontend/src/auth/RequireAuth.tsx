import { Center, Loader } from '@mantine/core'
import { Navigate, Outlet, useLocation } from 'react-router'
import { useAuth } from './AuthProvider'

/**
 * A same-site path to go to after login; anything else becomes `/`: other origins, `//host`, `/\host`,
 * and control characters (URL parsing drops tabs and newlines, so `/\t/host` would become `//host`).
 */
export function safeNext(next: string | null): string {
  // oxlint-disable-next-line no-control-regex
  const unsafe = !next || !next.startsWith('/') || next.startsWith('//') || next.startsWith('/\\') || /[\x00-\x1f\x7f]/.test(next)
  return unsafe ? '/' : next
}

/** Login that comes back to this page. */
export function useLoginPath(): string {
  const { pathname, search } = useLocation()
  return `/login?next=${encodeURIComponent(pathname + search)}`
}

function Loading() {
  return (
    <Center mt="xl">
      <Loader />
    </Center>
  )
}

/** For pages anyone can open: a returning user's session comes back first, so they aren't served as a visitor. */
export function WaitForAuth() {
  return useAuth().ready ? <Outlet /> : <Loading />
}

export default function RequireAuth() {
  const { user, ready } = useAuth()
  const loginPath = useLoginPath()
  if (!ready) return <Loading />
  if (!user) return <Navigate to={loginPath} replace />
  return <Outlet />
}
