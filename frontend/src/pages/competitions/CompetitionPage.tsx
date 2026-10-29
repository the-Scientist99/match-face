import { Badge, Group, Loader, Stack, Tabs, Text, Title } from '@mantine/core'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { useParams, useSearchParams } from 'react-router'
import { api, call, type Competition } from '../../api/client'
import { errorCode } from '../../api/errors'
import Crest from '../../components/Crest'
import EditableCrest, { RemoveImageButton } from '../../components/ImageUpload'
import BracketTab from './BracketTab'
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
    <Stack>
      <Group gap="md" wrap="nowrap">
        {c.canAdmin ? (
          <EditableCrest kind="competitions" id={c.id} name={c.name} imageUrl={c.imageUrl} size={64} />
        ) : (
          c.imageUrl && <Crest name={c.name} size={64} src={c.imageUrl} />
        )}
        <Stack gap={8} align="flex-start" miw={0}>
          <Title order={2}>{c.name}</Title>
          <Badge>
            {t(`competition.type.${c.type}`)} · {t(`competition.status.${c.status}`)}
          </Badge>
          {c.canAdmin && c.imageUrl && <RemoveImageButton kind="competitions" id={c.id} />}
        </Stack>
      </Group>
      <Tabs className="track-tabs" value={tab} onChange={(v) => setParams(v ? { tab: v } : {}, { replace: true })}>
        <Tabs.List>
          <Tabs.Tab value="fixtures">{t('competition.fixtures')}</Tabs.Tab>
          {c.type === 'CUP' ? (
            <Tabs.Tab value="bracket">{t('competition.bracket')}</Tabs.Tab>
          ) : (
            <Tabs.Tab value="table">{t('competition.table')}</Tabs.Tab>
          )}
          <Tabs.Tab value="stats">{t('competition.stats')}</Tabs.Tab>
          {c.canAdmin && <Tabs.Tab value="manage">{t('competition.manage')}</Tabs.Tab>}
        </Tabs.List>
        <Tabs.Panel value="fixtures" pt="md">
          <FixturesTab id={id} />
        </Tabs.Panel>
        <Tabs.Panel value="table" pt="md">
          {tab === 'table' && c.type === 'LEAGUE' && <TableTab id={id} />}
        </Tabs.Panel>
        <Tabs.Panel value="bracket" pt="md">
          {tab === 'bracket' && c.type === 'CUP' && <BracketTab id={id} />}
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
