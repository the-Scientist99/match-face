import { Center, Loader } from '@mantine/core'
import { Navigate, Outlet, useLocation } from 'react-router'
import { useAuth } from './AuthProvider'

/** A same-site path to go to after login; anything else (other origins, `//host`, `/\host`) becomes `/`. */
export function safeNext(next: string | null): string {
  return next && next.startsWith('/') && !next.startsWith('//') && !next.startsWith('/\\') ? next : '/'
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
