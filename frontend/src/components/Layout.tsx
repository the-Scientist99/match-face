import { AppShell, Button, Group, Menu, SegmentedControl, Title } from '@mantine/core'
import { useTranslation } from 'react-i18next'
import { Link, Outlet, useNavigate } from 'react-router'
import { useNotifyError } from '../api/errors'
import { useAuth } from '../auth/AuthProvider'
import type { Lang } from '../lang'

/** On small screens these move into the user menu. */
const NAV = [
  ['/players', 'nav.players'],
  ['/teams', 'nav.teams'],
] as const

export default function Layout() {
  const { t, i18n } = useTranslation()
  const { user, ready, logout, setLocale } = useAuth()
  const navigate = useNavigate()
  const notifyError = useNotifyError()
  return (
    <AppShell header={{ height: 56 }} padding="md">
      <AppShell.Header>
        <Group h="100%" px="md" justify="space-between" wrap="nowrap">
          <Title order={4} renderRoot={(props) => <Link to="/" {...props} />}>
            {t('app.name')}
          </Title>
          <Group gap="xs" wrap="nowrap">
            {user &&
              NAV.map(([to, key]) => (
                <Button key={to} component={Link} to={to} variant="subtle" size="xs" visibleFrom="sm">
                  {t(key)}
                </Button>
              ))}
            <SegmentedControl
              size="xs"
              value={i18n.language}
              onChange={(lng) => setLocale(lng as Lang).catch(notifyError)}
              data={[
                { value: 'me', label: t('language.me') },
                { value: 'en', label: t('language.en') },
              ]}
            />
            {user ? (
              <Menu position="bottom-end">
                <Menu.Target>
                  <Button variant="subtle" size="xs">
                    {user.displayName}
                  </Button>
                </Menu.Target>
                <Menu.Dropdown>
                  {NAV.map(([to, key]) => (
                    <Menu.Item key={to} component={Link} to={to} hiddenFrom="sm">
                      {t(key)}
                    </Menu.Item>
                  ))}
                  <Menu.Item onClick={() => logout().then(() => navigate('/login'))}>{t('auth.logout')}</Menu.Item>
                </Menu.Dropdown>
              </Menu>
            ) : (
              ready && (
                <>
                  <Button component={Link} to="/login" variant="subtle" size="xs">
                    {t('auth.login')}
                  </Button>
                  <Button component={Link} to="/register" size="xs">
                    {t('auth.register')}
                  </Button>
                </>
              )
            )}
          </Group>
        </Group>
      </AppShell.Header>
      <AppShell.Main>
        <Outlet />
      </AppShell.Main>
    </AppShell>
  )
}
