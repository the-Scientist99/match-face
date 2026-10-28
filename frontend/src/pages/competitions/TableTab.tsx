import { Loader, Table } from '@mantine/core'
import { useMediaQuery } from '@mantine/hooks'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { api, call, type StandingRow } from '../../api/client'

export default function TableTab({ id }: { id: string }) {
  const { t } = useTranslation()
  const wide = useMediaQuery('(min-width: 400px)') // GF and GA don't fit on the narrowest phones
  const standings = useQuery<StandingRow[]>({
    queryKey: ['competition', id, 'standings'],
    queryFn: () => call(api.GET('/api/competitions/{id}/standings', { params: { path: { id } } })),
  })
  if (standings.isPending) return <Loader />
  const cols: (keyof StandingRow)[] = ['played', 'won', 'drawn', 'lost', ...(wide ? (['goalsFor', 'goalsAgainst'] as const) : []), 'goalDifference', 'points']
  return (
    <Table striped fz="sm">
      <Table.Thead>
        <Table.Tr>
          <Table.Th>#</Table.Th>
          <Table.Th>{t('table.team')}</Table.Th>
          {cols.map((c) => (
            <Table.Th key={c} ta="right">
              {t(`table.${c}`)}
            </Table.Th>
          ))}
        </Table.Tr>
      </Table.Thead>
      <Table.Tbody>
        {standings.data?.map((r) => (
          <Table.Tr key={r.teamId}>
            <Table.Td>{r.position}</Table.Td>
            <Table.Td>{r.name}</Table.Td>
            {cols.map((c) => (
              <Table.Td key={c} ta="right" fw={c === 'points' ? 700 : undefined}>
                {r[c]}
              </Table.Td>
            ))}
          </Table.Tr>
        ))}
      </Table.Tbody>
    </Table>
  )
}
