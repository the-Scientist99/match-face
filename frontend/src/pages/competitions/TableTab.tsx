import { Group, Text } from '@mantine/core'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { api, call, type StandingRow } from '../../api/client'
import LoadError from '../../auth/LoadError'
import Crest from '../../components/Crest'
import Empty from '../../components/Empty'
import Loading from '../../components/Loading'

/** +3, 0, −2 (a real minus sign) */
const signed = (n: number) => (n > 0 ? `+${n}` : n < 0 ? `−${-n}` : '0')

const NUMBERS = ['played', 'won', 'drawn', 'lost', 'goalsFor', 'goalsAgainst'] as const

/** Every column at every width: on a phone the numbers scroll between the Team and Pts columns, which stay put. */
export default function TableTab({ id, name }: { id: string; name: string }) {
  const { t } = useTranslation()
  const standings = useQuery<StandingRow[]>({
    queryKey: ['competition', id, 'standings'],
    queryFn: () => call(api.GET('/api/competitions/{id}/standings', { params: { path: { id } } })),
  })
  if (standings.isPending) return <Loading rows={6} />
  if (standings.isError) return <LoadError error={standings.error} onRetry={() => void standings.refetch()} />
  if (!standings.data.length) return <Empty text={t('competition.noTeams')} />
  const heading = (key: (typeof NUMBERS)[number] | 'goalDifference' | 'points') => <abbr title={t(`table.${key}Full`)}>{t(`table.${key}`)}</abbr>
  return (
    // keyboard users can scroll it too
    <div className="table-scroll" role="region" aria-label={t('table.caption', { name })} tabIndex={0}>
      <table className="standings">
        <caption style={{ position: 'absolute', width: 1, height: 1, overflow: 'hidden', clipPath: 'inset(50%)' }}>{t('table.caption', { name })}</caption>
        <thead>
          <tr>
            <th scope="col" className="standings-team">
              {t('table.team')}
            </th>
            {NUMBERS.map((c) => (
              <th key={c} scope="col">
                {heading(c)}
              </th>
            ))}
            <th scope="col">{heading('goalDifference')}</th>
            <th scope="col" className="standings-pts">
              {heading('points')}
            </th>
          </tr>
        </thead>
        <tbody>
          {standings.data.map((r) => (
            <tr key={r.teamId}>
              <th scope="row" className="standings-team">
                <Group gap={8} wrap="nowrap">
                  <Text span c="dimmed" fw={600} miw={16}>
                    {r.position}
                  </Text>
                  <Crest name={r.name} size={28} />
                  {r.name}
                </Group>
              </th>
              {NUMBERS.map((c) => (
                <td key={c}>{r[c]}</td>
              ))}
              <td className="standings-gd">{signed(r.goalDifference)}</td>
              <td className="standings-pts score">{r.points}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
