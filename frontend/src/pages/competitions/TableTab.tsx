import { Group, Loader, Paper, Table, Text } from '@mantine/core'
import { useMediaQuery } from '@mantine/hooks'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { api, call, type StandingRow } from '../../api/client'
import { tintOf } from '../../components/Crest'

/** +3, 0, −2 (a real minus sign) */
const signed = (n: number) => (n > 0 ? `+${n}` : n < 0 ? `−${-n}` : '0')

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
    <Paper p="xs">
      <Table fz="sm" className="standings" verticalSpacing="sm" horizontalSpacing={6} highlightOnHover>
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
          {standings.data?.map((r, i) => (
            <Table.Tr key={r.teamId}>
              <Table.Td>
                {i === 0 ? (
                  <span className="pos-leader">{r.position}</span>
                ) : (
                  <Text span size="sm" c="dimmed" fw={600} px={6}>
                    {r.position}
                  </Text>
                )}
              </Table.Td>
              <Table.Td>
                <Group gap={8} wrap="nowrap" data-tint={tintOf(r.name)}>
                  <span className="tint-dot" style={{ width: 8, height: 8 }} />
                  <Text span size="sm" fw={i === 0 ? 700 : 600}>
                    {r.name}
                  </Text>
                </Group>
              </Table.Td>
              {cols.map((c) =>
                c === 'points' ? (
                  <Table.Td key={c} ta="right" className="score" fz={19} fw={700}>
                    {r.points}
                  </Table.Td>
                ) : c === 'goalDifference' ? (
                  <Table.Td key={c} ta="right" fw={600} c={r.goalDifference > 0 ? 'teal' : 'dimmed'}>
                    {signed(r.goalDifference)}
                  </Table.Td>
                ) : (
                  <Table.Td key={c} ta="right">
                    {r[c]}
                  </Table.Td>
                ),
              )}
            </Table.Tr>
          ))}
        </Table.Tbody>
      </Table>
    </Paper>
  )
}
