import { Badge, Group, Loader, Stack, Tabs, Text, Title } from '@mantine/core'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { useParams, useSearchParams } from 'react-router'
import { api, call, type Competition } from '../../api/client'
import { errorCode } from '../../api/errors'
import FixturesTab from './FixturesTab'
import ManageTab from './ManageTab'
import StatsTab from './StatsTab'
import TableTab from './TableTab'

export default function CompetitionPage() {
  const { t } = useTranslation()
  const { id = '' } = useParams()
  const [params, setParams] = useSearchParams()
  const competition = useQuery<Competition>({
    queryKey: ['competition', id],
    queryFn: () => call(api.GET('/api/competitions/{id}', { params: { path: { id } } })),
    retry: false,
  })

  if (competition.isPending) return <Loader />
  if (competition.isError) return <Text>{t(`errors.${errorCode(competition.error)}`, { defaultValue: t('errors.UNKNOWN') })}</Text>
  const c = competition.data
  const tab = params.get('tab') ?? 'fixtures'

  return (
    <Stack maw={800}>
      <Group gap="xs">
        <Title order={2}>{c.name}</Title>
        <Badge variant="light">{t(`competition.status.${c.status}`)}</Badge>
      </Group>
      <Tabs value={tab} onChange={(v) => setParams(v ? { tab: v } : {}, { replace: true })}>
        <Tabs.List>
          <Tabs.Tab value="fixtures">{t('competition.fixtures')}</Tabs.Tab>
          <Tabs.Tab value="table">{t('competition.table')}</Tabs.Tab>
          <Tabs.Tab value="stats">{t('competition.stats')}</Tabs.Tab>
          {c.canAdmin && <Tabs.Tab value="manage">{t('competition.manage')}</Tabs.Tab>}
        </Tabs.List>
        <Tabs.Panel value="fixtures" pt="md">
          <FixturesTab id={id} />
        </Tabs.Panel>
        <Tabs.Panel value="table" pt="md">
          {tab === 'table' && <TableTab id={id} />}
        </Tabs.Panel>
        <Tabs.Panel value="stats" pt="md">
          {tab === 'stats' && <StatsTab id={id} />}
        </Tabs.Panel>
        {c.canAdmin && (
          <Tabs.Panel value="manage" pt="md">
            <ManageTab competition={c} />
          </Tabs.Panel>
        )}
      </Tabs>
    </Stack>
  )
}
