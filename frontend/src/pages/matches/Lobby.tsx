import { Group, SegmentedControl, Stack, Text, VisuallyHidden } from '@mantine/core'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import type { Match } from '../../api/client'
import { useAuth } from '../../auth/AuthProvider'
import Crest, { sideTints } from '../../components/Crest'
import Empty from '../../components/Empty'
import { htmlLang, type Lang } from '../../lang'
import { pitchRows, shirtNames } from './matchLogic'

type Side = 'HOME' | 'AWAY'
type Slot =
  | { kind: 'player'; id: string; name: string; full: string; keeper: boolean; you: boolean }
  | { kind: 'join'; id: string }
  | { kind: 'open'; id: string }

/** A pitch holds eleven a side; a bigger cap just shows the room left up to that. */
const MOST_ON_PITCH = 11
/** More than this stops fitting a phone, so such a match opens on the list. */
const PITCH_FITS = 9

/** The app's keeper jersey, filled in the side's colour. */
const Shirt = () => (
  <svg width={44} height={44} viewBox="0 0 24 24" aria-hidden>
    <path d="M7 21V11l-2-3 2-4 3 1 2-1 2 1 3-1 2 4-2 3v10z" />
  </svg>
)

function Token({ slot, sideName, onJoin, disabled }: { slot: Slot; sideName: string | null; onJoin: () => void; disabled: boolean }) {
  const { t } = useTranslation()
  if (slot.kind === 'join') {
    return (
      <button type="button" className="shirt-token" data-empty aria-label={t('match.join', { side: sideName })} disabled={disabled} onClick={onJoin}>
        <Shirt />
        <span className="shirt-name">{t('match.joinShort')}</span>
      </button>
    )
  }
  if (slot.kind === 'open') {
    return (
      <span className="shirt-token" data-empty data-open aria-hidden>
        <Shirt />
      </span>
    )
  }
  const sub = slot.you ? t('match.you') : slot.keeper ? t('match.keeper') : null
  return (
    <Link className="shirt-token" data-keeper={slot.keeper || undefined} to={`/players/${slot.id}`} aria-label={sub ? `${slot.full}, ${sub}` : slot.full}>
      <Shirt />
      <span className="shirt-name">{slot.name}</span>
      {sub && <span className="shirt-sub">{sub}</span>}
    </Link>
  )
}

/** Who's where: both sides on one pitch, keepers at the back, and each open spot a shirt to tap. */
export default function Lobby({ match: m, onJoin, disabled }: { match: Match; onJoin: (side: Side) => void; disabled: boolean }) {
  const { t } = useTranslation()
  const { user } = useAuth()
  const count = (side: Side) => m.lineup.filter((l) => l.side === side).length
  const [view, setView] = useState(Math.max(count('HOME'), count('AWAY')) > PITCH_FITS ? 'list' : 'pitch')
  const tints = sideTints(m.home.name, m.away.name)
  const sideName = (side: Side) => (side === 'HOME' ? m.home.name : m.away.name)
  const canJoin = m.joinable && !m.mySide && !!user

  const slotsFor = (side: Side) => {
    const lineup = m.lineup.filter((l) => l.side === side)
    const names = shirtNames(lineup.map((l) => l.name))
    const players: Slot[] = lineup.map((l, i) => ({ kind: 'player', id: l.playerId, name: names[i], full: l.name, keeper: l.inGoal, you: l.playerId === user?.playerId }))
    const room = m.maxPerSide === null ? (canJoin ? 1 : 0) : m.maxPerSide - lineup.length
    const empties: Slot[] = Array.from({ length: Math.max(Math.min(room, MOST_ON_PITCH - lineup.length), 0) }, (_, i) => ({ kind: canJoin ? 'join' : 'open', id: `${side}${i}` }))
    return [...players.filter((p) => p.kind === 'player' && p.keeper), ...players.filter((p) => p.kind === 'player' && !p.keeper), ...empties]
  }
  const rowsFor = (side: Side) => {
    const slots = slotsFor(side)
    const keepers = slots.filter((s) => s.kind === 'player' && s.keeper)
    return [...(keepers.length ? [keepers] : []), ...pitchRows(slots.slice(keepers.length))]
  }
  const spots = m.maxPerSide === null ? null : (['HOME', 'AWAY'] as const).reduce((n, side) => n + Math.max(m.maxPerSide! - count(side), 0), 0)
  const nobody = !m.lineup.length && !canJoin && !m.maxPerSide

  return (
    <Stack gap="md">
      <Group justify="space-between" gap="sm">
        <SegmentedControl
          aria-label={t('match.lineupView')}
          value={view}
          onChange={setView}
          data={[
            { value: 'pitch', label: t('match.pitch') },
            { value: 'list', label: t('match.list') },
          ]}
        />
        {spots !== null && (
          <Text fw={600} c={spots ? 'teal' : 'dimmed'} role="status">
            {spots ? t('match.spotsLeft', { count: spots }) : t('match.full')}
          </Text>
        )}
      </Group>
      {nobody ? (
        <Empty text={t('teams.emptyRoster')} />
      ) : view === 'pitch' ? (
        <section className="pitch" aria-label={t('match.lineup')}>
          <span className="pitch-mid" aria-hidden />
          <span className="pitch-spot" aria-hidden />
          <span className="pitch-box" data-side="HOME" aria-hidden />
          <span className="pitch-box" data-side="AWAY" aria-hidden />
          {(['HOME', 'AWAY'] as const).map((side) => (
            <div key={side} className="half" data-side={side} data-tint={tints[side]} role="group" aria-label={sideName(side) ?? undefined}>
              {rowsFor(side).map((row, i) => (
                <div key={i} className="pitch-line">
                  {row.map((slot) => (
                    <Token key={slot.id} slot={slot} sideName={sideName(side)} disabled={disabled} onJoin={() => onJoin(side)} />
                  ))}
                </div>
              ))}
            </div>
          ))}
        </section>
      ) : (
        <div className="split">
          {(['HOME', 'AWAY'] as const).map((side) => (
            <section key={side} className="surface" aria-label={sideName(side) ?? undefined}>
              <h2 className="section-title">{sideName(side)}</h2>
              <div className="list-rows">
                {slotsFor(side).map(
                  (slot) =>
                    slot.kind === 'player' && (
                      <Group key={slot.id} justify="space-between" wrap="nowrap" mih={48}>
                        <Link to={`/players/${slot.id}`} style={{ color: 'inherit', fontWeight: 600, textDecoration: 'none' }}>
                          {slot.full}
                        </Link>
                        {(slot.you || slot.keeper) && (
                          <Text size="xs" c="dimmed">
                            {slot.you ? t('match.you') : t('match.keeper')}
                          </Text>
                        )}
                      </Group>
                    ),
                )}
              </div>
              {canJoin && (m.maxPerSide === null || count(side) < m.maxPerSide) && (
                <button type="button" className="row-link" disabled={disabled} onClick={() => onJoin(side)}>
                  {t('match.join', { side: sideName(side) })}
                </button>
              )}
            </section>
          ))}
        </div>
      )}
    </Stack>
  )
}

/** The lobby's dark board: how full each side is either side of the kickoff card. */
export function LobbyHead({ match: m }: { match: Match }) {
  const { t, i18n } = useTranslation()
  const locale = htmlLang(i18n.language as Lang)
  const tints = sideTints(m.home.name, m.away.name)
  const kickoff = new Date(m.kickoffAt!)
  const side = (side: Side) => {
    const n = m.lineup.filter((l) => l.side === side).length
    const info = side === 'HOME' ? m.home : m.away
    return (
      <div className="lobby-side">
        <span className="score lobby-count">{m.maxPerSide === null ? n : `${n}/${m.maxPerSide}`}</span>
        <Group gap={6} justify="center">
          <Crest name={info.name} tint={tints[side]} size={24} src={info.imageUrl} />
          <Text fw={600} size="sm" lh={1.25}>
            {info.name ?? t('bracket.tbd')}
          </Text>
        </Group>
      </div>
    )
  }
  return (
    <section className="board" aria-labelledby="match-title">
      <VisuallyHidden component="h1" id="match-title">
        {m.home.name} {t('match.vs')} {m.away.name}
      </VisuallyHidden>
      <div className="lobby-head">
        {side('HOME')}
        <div className="kickoff-card">
          <div>
            <time className="score kickoff-time" dateTime={m.kickoffAt!} style={{ display: 'block' }}>
              {kickoff.toLocaleTimeString(locale, { hour: '2-digit', minute: '2-digit' })}
            </time>
            <span className="kickoff-day">{kickoff.toLocaleDateString(locale, { weekday: 'short', day: 'numeric', month: 'short' })}</span>
          </div>
          {m.venue && <p>{m.venue}</p>}
        </div>
        {side('AWAY')}
      </div>
    </section>
  )
}
