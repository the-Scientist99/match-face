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

export default function RequireAuth() {
  const { user, ready } = useAuth()
  const location = useLocation()
  if (!ready) {
    return (
      <Center mt="xl">
        <Loader />
      </Center>
    )
  }
  if (!user) return <Navigate to={`/login?next=${encodeURIComponent(location.pathname + location.search)}`} replace />
  return <Outlet />
}
