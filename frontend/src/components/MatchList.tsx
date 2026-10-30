import { Group, Stack, Text } from '@mantine/core'
import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import type { MatchSummary } from '../api/client'
import { htmlLang, type Lang } from '../lang'
import Crest from './Crest'
import { ChevronIcon } from './icons'

export function formatKickoff(iso: string | null, lang: string): string | null {
  return iso ? new Date(iso).toLocaleString(htmlLang(lang as Lang), { dateStyle: 'medium', timeStyle: 'short' }) : null
}

/** One team per line: crest, name, and the goals once played. The losing side is quieter. */
function TeamLine({ name, image, goals, result }: { name: string | null; image: string | null; goals?: ReactNode; result?: 'won' | 'lost' }) {
  const { t } = useTranslation()
  const lost = result === 'lost'
  return (
    <Group wrap="nowrap" gap="sm">
      <Crest name={name} src={image} />
      <Text flex={1} fw={result === 'won' ? 700 : lost ? 500 : 600} c={lost || !name ? 'dimmed' : undefined} truncate>
        {name ?? t('bracket.tbd')}
      </Text>
      {goals !== undefined && (
        <Text className="score" fz={24} lh={1} c={lost ? 'dimmed' : undefined}>
          {goals}
        </Text>
      )}
    </Group>
  )
}

/** Upcoming: a date column beside the two teams. */
function Upcoming({ m }: { m: MatchSummary }) {
  const { t, i18n } = useTranslation()
  const locale = htmlLang(i18n.language as Lang)
  const d = m.kickoffAt ? new Date(m.kickoffAt) : null
  return (
    <Group wrap="nowrap" gap="md">
      {d ? (
        <time className="date-block" dateTime={m.kickoffAt!} title={formatKickoff(m.kickoffAt, i18n.language)!}>
          <span className="date-block-day">{d.toLocaleDateString(locale, { weekday: 'short' })}</span>
          <span className="score date-block-num">{d.getDate()}</span>
          <span className="date-block-time">{d.toLocaleTimeString(locale, { hour: 'numeric', minute: '2-digit' })}</span>
        </time>
      ) : (
        <span className="date-block">
          <span className="score date-block-num" aria-hidden>
            –
          </span>
        </span>
      )}
      <Stack gap={6} flex={1} miw={0}>
        <TeamLine name={m.homeName} image={m.homeImageUrl} />
        <TeamLine name={m.awayName} image={m.awayImageUrl} />
        {m.spotsLeft !== null && (
          <Text size="xs" fw={600} c={m.spotsLeft ? 'teal' : 'dimmed'}>
            {m.spotsLeft ? t('match.spotsLeft', { count: m.spotsLeft }) : t('match.full')}
          </Text>
        )}
      </Stack>
      <Text c="dimmed" component="span" display="flex">
        <ChevronIcon size={18} />
      </Text>
    </Group>
  )
}

/** Played or cancelled: the two teams with their goals, then the status, day and competition. */
function Result({ m }: { m: MatchSummary }) {
  const { t, i18n } = useTranslation()
  const cancelled = m.status === 'CANCELLED'
  const diff = m.score.home - m.score.away
  const result = (sign: number) => (cancelled ? 'lost' : diff * sign > 0 ? 'won' : diff * sign < 0 ? 'lost' : undefined)
  const day = m.kickoffAt && new Date(m.kickoffAt).toLocaleDateString(htmlLang(i18n.language as Lang), { weekday: 'short', day: 'numeric', month: 'short' })
  return (
    <Stack gap={6}>
      <TeamLine name={m.homeName} image={m.homeImageUrl} goals={cancelled ? '–' : m.score.home} result={result(1)} />
      <TeamLine name={m.awayName} image={m.awayImageUrl} goals={cancelled ? '–' : m.score.away} result={result(-1)} />
      <Text size="xs" c="dimmed" truncate>
        {[t(`match.status.${m.status}`), day, m.competitionName].filter(Boolean).join(' · ')}
      </Text>
    </Stack>
  )
}

/** One panel of ruled rows: upcoming ones lead with the date, played ones with the result. Reused by competitions (M5). */
export default function MatchList({ matches, extra }: { matches: MatchSummary[]; extra?: (m: MatchSummary) => ReactNode }) {
  return (
    <div className="rows">
      {matches.map((m) => (
        <Link key={m.id} className="row" to={`/matches/${m.id}`}>
          {m.status === 'SCHEDULED' ? <Upcoming m={m} /> : <Result m={m} />}
          {extra?.(m)}
        </Link>
      ))}
    </div>
  )
}
