import { AppShell, Group, SegmentedControl, Title } from '@mantine/core'
import { useTranslation } from 'react-i18next'
import { Link, Outlet } from 'react-router'

export default function Layout() {
  const { t, i18n } = useTranslation()
  return (
    <AppShell header={{ height: 56 }} padding="md">
      <AppShell.Header>
        <Group h="100%" px="md" justify="space-between" wrap="nowrap">
          <Title order={4} renderRoot={(props) => <Link to="/" {...props} />}>
            {t('app.name')}
          </Title>
          <SegmentedControl
            size="xs"
            value={i18n.language}
            onChange={(lng) => i18n.changeLanguage(lng)}
            data={[
              { value: 'me', label: t('language.me') },
              { value: 'en', label: t('language.en') },
            ]}
          />
        </Group>
      </AppShell.Header>
      <AppShell.Main>
        <Outlet />
      </AppShell.Main>
    </AppShell>
  )
}
