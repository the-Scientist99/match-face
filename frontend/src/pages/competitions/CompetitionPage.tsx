import { Button, CopyButton, Group, Stack, Tabs, Text, ThemeIcon, Title } from '@mantine/core'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { useParams, useSearchParams } from 'react-router'
import { api, call, type Competition } from '../../api/client'
import LoadError from '../../auth/LoadError'
import Crest from '../../components/Crest'
import { ListIcon, TrophyIcon } from '../../components/icons'
import EditableCrest, { RemoveImageButton } from '../../components/ImageUpload'
import Loading from '../../components/Loading'
import { usePageTitle } from '../../components/usePageTitle'
import BracketTab from './BracketTab'
import EnterTeam from './EnterTeam'
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

  usePageTitle(competition.data?.name)
  if (competition.isPending) return <Loading />
  if (competition.isError) return <LoadError error={competition.error} />
  const c = competition.data
  const tab = params.get('tab') ?? 'fixtures'

  return (
    <Stack gap="lg">
      <Group gap="md">
        {c.canAdmin ? (
          <EditableCrest kind="competitions" id={c.id} name={c.name} imageUrl={c.imageUrl} size={72} />
        ) : c.imageUrl ? (
          <Crest name={c.name} size={72} src={c.imageUrl} />
        ) : (
          <ThemeIcon size={72} radius="lg" color="dark.8" aria-hidden>
            {c.type === 'CUP' ? <TrophyIcon size={32} /> : <ListIcon size={32} />}
          </ThemeIcon>
        )}
        <Stack gap={2} align="flex-start" miw={0} flex="1 1 220px">
          <Title order={1} style={{ overflowWrap: 'anywhere' }}>
            {c.name}
          </Title>
          <Text c="dimmed" fw={500}>
            {t(`competition.type.${c.type}`)} · {t(`competition.status.${c.status}`)}
          </Text>
          {c.canAdmin && c.imageUrl && <RemoveImageButton kind="competitions" id={c.id} />}
        </Stack>
        {c.visibility === 'PUBLIC' && (
          <CopyButton value={`${window.location.origin}/competitions/${c.id}`}>
            {({ copied, copy }) => (
              <Button variant="default" onClick={copy} aria-live="polite">
                {copied ? t('claim.copied') : t('claim.copy')}
              </Button>
            )}
          </CopyButton>
        )}
      </Group>
      {!c.canAdmin && (c.entriesOpen || c.entries.length > 0) && <EnterTeam competition={c} />}
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
        <Tabs.Panel value="fixtures" pt="lg">
          <FixturesTab id={id} canAdmin={c.canAdmin} />
        </Tabs.Panel>
        <Tabs.Panel value="table" pt="lg">
          {tab === 'table' && c.type === 'LEAGUE' && <TableTab id={id} name={c.name} />}
        </Tabs.Panel>
        <Tabs.Panel value="bracket" pt="lg">
          {tab === 'bracket' && c.type === 'CUP' && <BracketTab id={id} />}
        </Tabs.Panel>
        <Tabs.Panel value="stats" pt="lg">
          {tab === 'stats' && <StatsTab id={id} />}
        </Tabs.Panel>
        {c.canAdmin && (
          <Tabs.Panel value="manage" pt="lg">
            <ManageTab competition={c} />
          </Tabs.Panel>
        )}
      </Tabs>
    </Stack>
  )
}
