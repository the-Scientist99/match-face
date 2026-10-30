import { Group, SimpleGrid, Stack, Text } from '@mantine/core'
import { useQuery } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { api, call, type CompetitionStats } from '../../api/client'
import LoadError from '../../auth/LoadError'
import { CardIcon } from '../../components/icons'
import Loading from '../../components/Loading'

function Cards({ yellow, red }: { yellow: number; red: number }) {
  const { t } = useTranslation()
  return (
    <Group gap="sm" wrap="nowrap">
      <Group gap={4} wrap="nowrap">
        <CardIcon color="yellow" label={t('event.type.YELLOW')} />
        {yellow}
      </Group>
      <Group gap={4} wrap="nowrap">
        <CardIcon color="red" label={t('event.type.RED')} />
        {red}
      </Group>
    </Group>
  )
}

function Board({ title, rows }: { title: string; rows: { key: string; name: string; value: ReactNode }[] }) {
  const { t } = useTranslation()
  return (
    <Stack gap={6} className="surface">
      <h2 className="section-title">{title}</h2>
      {rows.length ? (
        rows.map((r) => (
          <Group key={r.key} justify="space-between" wrap="nowrap" mih={44}>
            <Text fw={600} truncate>
              {r.name}
            </Text>
            <Text component="div" className="score" fz={24}>
              {r.value}
            </Text>
          </Group>
        ))
      ) : (
        <Text size="sm" c="dimmed">
          {t('stats.none')}
        </Text>
      )}
    </Stack>
  )
}

export default function StatsTab({ id }: { id: string }) {
  const { t } = useTranslation()
  const stats = useQuery<CompetitionStats>({
    queryKey: ['competition', id, 'stats'],
    queryFn: () => call(api.GET('/api/competitions/{id}/stats', { params: { path: { id } } })),
  })
  if (stats.isPending) return <Loading />
  if (stats.isError) return <LoadError error={stats.error} onRetry={() => void stats.refetch()} />
  const s = stats.data
  return (
    <SimpleGrid cols={{ base: 1, sm: 3 }}>
      <Board title={t('stats.topScorers')} rows={s.topScorers.map((l) => ({ key: l.playerId, name: l.name, value: String(l.value) }))} />
      <Board title={t('stats.topAssists')} rows={s.topAssists.map((l) => ({ key: l.playerId, name: l.name, value: String(l.value) }))} />
      <Board title={t('stats.cards')} rows={s.cards.map((c) => ({ key: c.playerId, name: c.name, value: <Cards yellow={c.yellow} red={c.red} /> }))} />
    </SimpleGrid>
  )
}
