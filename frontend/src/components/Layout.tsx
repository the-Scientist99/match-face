import { ActionIcon, AppShell, Button, Container, Group, type MantineColorScheme, Menu, SegmentedControl, Title, useMantineColorScheme } from '@mantine/core'
import { useMediaQuery } from '@mantine/hooks'
import { useTranslation } from 'react-i18next'
import { Link, Outlet, useLocation, useNavigate } from 'react-router'
import { useNotifyError } from '../api/errors'
import { useAuth } from '../auth/AuthProvider'
import type { Lang } from '../lang'
import { initials } from './Crest'
import Logo from './Logo'
import { BallIcon, PersonIcon, ShieldIcon } from './icons'

/** Header links on wider screens, the bottom tab bar on phones */
const TABS = [
  ['/', 'nav.matches', BallIcon],
  ['/players', 'nav.players', PersonIcon],
  ['/teams', 'nav.teams', ShieldIcon],
] as const
/** Matches is current on its match and competition pages too, not only on `/` */
const isCurrent = (to: string, path: string) => (to === '/' ? path === '/' || /^\/(matches|competitions)\//.test(path) : path.startsWith(to))
const LANGS: Lang[] = ['me', 'en']
/** One width for every page and the header, so their edges line up on wide screens */
const PAGE_WIDTH = 960
const SCHEMES: MantineColorScheme[] = ['auto', 'light', 'dark']

export default function Layout() {
  const { t, i18n } = useTranslation()
  const { user, ready, logout, setLocale } = useAuth()
  const navigate = useNavigate()
  const { pathname } = useLocation()
  const current = (to: string) => (isCurrent(to, pathname) ? 'page' : undefined)
  const notifyError = useNotifyError()
  const phone = useMediaQuery('(max-width: 47.99em)') // below Mantine's sm
  const changeLang = (lng: string) => setLocale(lng as Lang).catch(notifyError)
  // auto follows the device; Mantine keeps the choice in this browser's localStorage
  const { colorScheme, setColorScheme } = useMantineColorScheme()
  return (
    <AppShell header={{ height: 60 }} footer={{ height: 64, collapsed: !(user && phone) }} padding="md">
      <AppShell.Header className="app-header" withBorder={false}>
        <Container size={PAGE_WIDTH} h="100%" px="md">
          <Group h="100%" justify="space-between" wrap="nowrap">
            <Title order={4} className="brand" renderRoot={(props) => <Link to="/" {...props} />}>
              <Logo />
            </Title>
            <Group gap="xs" wrap="nowrap">
              {user ? (
                <>
                  {/* the phone tab bar's links, with the current page marked the same way */}
                  <Group gap={4} visibleFrom="sm" className="header-nav" component="nav" aria-label={t('nav.main')}>
                    {TABS.map(([to, key]) => (
                      <Button key={to} component={Link} to={to} aria-current={current(to)} variant="subtle" color="gray" size="sm">
                        {t(key)}
                      </Button>
                    ))}
                  </Group>
                  {/* language lives in the account menu, so a phone's header keeps room for the brand */}
                  <Menu position="bottom-end" width={260}>
                    <Menu.Target>
                      <ActionIcon size={44} radius="xl" variant="light" fz="sm" fw={700} aria-label={t('nav.account')}>
                        {initials(user.displayName)}
                      </ActionIcon>
                    </Menu.Target>
                    <Menu.Dropdown>
                      <Menu.Label>{user.displayName}</Menu.Label>
                      <Menu.Divider />
                      {/* one row each; not menu items, so picking one keeps the menu open */}
                      <Menu.Label>{t('language.label')}</Menu.Label>
                      <SegmentedControl
                        fullWidth
                        size="xs"
                        mx="xs"
                        mb="xs"
                        value={i18n.language}
                        onChange={changeLang}
                        data={LANGS.map((lng) => ({ value: lng, label: t(`language.${lng}`) }))}
                      />
                      <Menu.Label>{t('theme.label')}</Menu.Label>
                      <SegmentedControl
                        fullWidth
                        size="xs"
                        mx="xs"
                        mb="xs"
                        value={colorScheme}
                        onChange={(v) => setColorScheme(v as MantineColorScheme)}
                        data={SCHEMES.map((s) => ({ value: s, label: t(`theme.${s}`) }))}
                      />
                      <Menu.Divider />
                      {/* Leave first: logging out on a protected page would send its guard to /login?next=<page>, and the next person to log in would land there */}
                      <Menu.Item onClick={async () => { await navigate('/', { flushSync: true }); await logout() }}>{t('auth.logout')}</Menu.Item>
                    </Menu.Dropdown>
                  </Menu>
                </>
              ) : (
                <>
                  <SegmentedControl
                    size="xs"
                    value={i18n.language}
                    onChange={changeLang}
                    data={LANGS.map((lng) => ({ value: lng, label: t(`language.${lng}`) }))}
                  />
                  {ready && (
                    <>
                      <Button component={Link} to="/login" variant="subtle" size="xs">
                        {t('auth.login')}
                      </Button>
                      {/* phones: the login page links to Register, so the header keeps room for the brand */}
                      <Button component={Link} to="/register" size="xs" visibleFrom="xs">
                        {t('auth.register')}
                      </Button>
                    </>
                  )}
                </>
              )}
            </Group>
          </Group>
        </Container>
      </AppShell.Header>
      <AppShell.Main>
        <Container size={PAGE_WIDTH} px={0} pb="xl">
          <Outlet />
        </Container>
      </AppShell.Main>
      {user && phone && (
        <AppShell.Footer className="app-header tab-bar" withBorder={false}>
          <nav aria-label={t('nav.main')}>
            {TABS.map(([to, key, Icon]) => (
              <Link key={to} to={to} aria-current={current(to)}>
                <Icon size={22} />
                {t(key)}
              </Link>
            ))}
          </nav>
        </AppShell.Footer>
      )}
    </AppShell>
  )
}
