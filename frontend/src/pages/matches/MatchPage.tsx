import { Alert, Anchor, Badge, Button, Card, Group, Loader, Modal, Stack, Text, VisuallyHidden } from '@mantine/core'
import { notifications } from '@mantine/notifications'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useParams } from 'react-router'
import { api, call, type Match } from '../../api/client'
import { errorCode, useNotifyError } from '../../api/errors'
import { sideTints } from '../../components/Crest'
import { KeeperIcon } from '../../components/icons'
import EventEntry, { type NewEvent } from './EventEntry'
import EditDetailsDialog from './EditDetailsDialog'
import EventList from './EventList'
import FinishDialog from './FinishDialog'
import { formatKickoff } from '../../components/MatchList'
import { hasStarted, teamsUndecided } from './matchLogic'
import LineupEditor from './LineupEditor'
import RsvpCard from './RsvpCard'
import ScoreHeader from './ScoreHeader'

export default function MatchPage() {
  const { t, i18n } = useTranslation()
  const { id = '' } = useParams()
  const queryClient = useQueryClient()
  const notifyError = useNotifyError()
  const path = { params: { path: { id } } }
  const match = useQuery<Match>({
    queryKey: ['match', id],
    queryFn: () => call(api.GET('/api/matches/{id}', path)),
    retry: false,
  })

  // Every write returns the whole match: store it as-is. One mutation for all of them, so while any is
  // in flight every control is disabled and a double tap can't add an event twice.
  const action = useMutation({
    mutationFn: (run: () => Promise<Match>) => run(),
    onSuccess: (updated) => {
      queryClient.setQueryData(['match', id], updated)
      void queryClient.invalidateQueries({ queryKey: ['matches'] })
      if (updated.competitionId) void queryClient.invalidateQueries({ queryKey: ['competition', updated.competitionId] })
    },
    onError: (err) => {
      if (errorCode(err) === 'STALE') {
        void match.refetch()
        notifications.show({ color: 'yellow', message: t('errors.STALE') })
      } else notifyError(err)
    },
  })
  const busy = action.isPending
  const [failedEvent, setFailedEvent] = useState<NewEvent | null>(null)
  const [finishing, setFinishing] = useState(false)
  const [cancelling, setCancelling] = useState(false)
  const [editing, setEditing] = useState(false)
  const [lineupOpen, setLineupOpen] = useState(false)

  if (match.isPending) return <Loader />
  if (match.isError) return <Text>{t(`errors.${errorCode(match.error)}`, { defaultValue: t('errors.UNKNOWN') })}</Text>
  const m = match.data
  // a cup match waiting for an earlier round: nothing can be entered or finished yet
  const editable = m.canAdmin && m.status === 'SCHEDULED' && !teamsUndecided(m)
  // before kickoff only the lineup can change: goals and the result wait for the match to start
  const started = hasStarted(m.kickoffAt)
  const version = { version: m.version }
  const run = (fn: () => Promise<unknown>) => action.mutate(fn as () => Promise<Match>)
  // A failed event save stays on screen with a retry, so nothing typed at the pitch is lost (spec 8).
  const addEvent = (e: NewEvent) =>
    action.mutate(() => call(api.POST('/api/matches/{id}/events', { ...path, body: e })) as Promise<Match>, {
      onSuccess: () => setFailedEvent(null),
      onError: () => setFailedEvent(e),
    })
  const lineupCount = (side: 'HOME' | 'AWAY') => m.lineup.filter((l) => l.side === side).length
  const playerName = (playerId?: string) => m.lineup.find((l) => l.playerId === playerId)?.name ?? t('event.unknownScorer')
  const deleteEvent = (eventId: string) =>
    run(() => call(api.DELETE('/api/matches/{id}/events/{eventId}', { params: { path: { id, eventId } } })))

  return (
    <Stack gap="lg">
      {m.competitionId && (
        <Group>
          <Badge component={Link} to={`/competitions/${m.competitionId}`} size="lg" className="pressable" style={{ textTransform: 'none' }}>
            {m.competitionName}
            {m.round !== null && ` · ${t('competition.round', { round: m.round })}`}
          </Badge>
        </Group>
      )}
      <ScoreHeader match={m} />
      {failedEvent && m.status === 'SCHEDULED' && (
        <Alert color="red" title={t('event.notSaved')}>
          <Group justify="space-between">
            <Text size="sm">
              {t(`event.type.${failedEvent.type}`)} · {playerName(failedEvent.playerId)}
              {failedEvent.minute !== undefined && ` · ${failedEvent.minute}'`}
            </Text>
            <Group gap="xs">
              <Button size="xs" disabled={busy} onClick={() => addEvent(failedEvent)}>
                {t('event.retry')}
              </Button>
              <Button size="xs" variant="subtle" onClick={() => setFailedEvent(null)}>
                {t('event.dismiss')}
              </Button>
            </Group>
          </Group>
        </Alert>
      )}
      {/* wide screens: entry, lineup and actions on the left, the timeline beside them and in view.
          phones keep one column in the order entry, events, lineup, actions (via `order`) */}
      <div className="split" data-interleave>
        <div>
          {editable && started && <EventEntry match={m} onAdd={addEvent} disabled={busy} />}
          {editable && !started && (
            <Group justify="space-between" wrap="nowrap" gap="sm">
              <Text c="dimmed">{t('match.startsAt', { time: formatKickoff(m.kickoffAt, i18n.language) })}</Text>
              <Button variant="light" disabled={busy} style={{ flexShrink: 0 }} onClick={() => run(() => call(api.POST('/api/matches/{id}/start', { ...path, body: version })))}>
                {t('match.kickOffNow')}
              </Button>
            </Group>
          )}
          {teamsUndecided(m) && <Text c="dimmed">{t('match.waitingForTeams')}</Text>}
          <Stack gap="lg" style={{ order: 2 }}>
            <RsvpCard
              match={m}
              disabled={busy}
              onAnswer={(going) => run(() => call(api.PUT('/api/matches/{id}/rsvp', { ...path, body: { going } })))}
            />
            {m.joinable &&
              (m.mySide ? (
                <Button size="lg" fullWidth variant="default" disabled={busy} onClick={() => run(() => call(api.DELETE('/api/matches/{id}/join', path)))}>
                  {t('match.leave')}
                </Button>
              ) : (
                <Group grow>
                  {(['HOME', 'AWAY'] as const).map((side) => {
                    const full = m.maxPerSide !== null && lineupCount(side) >= m.maxPerSide
                    return (
                      <Button key={side} size="lg" disabled={busy || full} onClick={() => run(() => call(api.POST('/api/matches/{id}/join', { ...path, body: { side } })))}>
                        <Stack gap={0} align="center">
                          <span>{t('match.join', { side: side === 'HOME' ? m.home.name : m.away.name })}</span>
                          {m.maxPerSide !== null && (
                            <Text span size="xs" fw={500}>
                              {full ? t('match.full') : `${lineupCount(side)}/${m.maxPerSide}`}
                            </Text>
                          )}
                        </Stack>
                      </Button>
                    )
                  })}
                </Group>
              ))}
            {editable ? (
              <>
                {/* once there is a lineup it folds into one row; an empty one stays open, events need players */}
                {m.lineup.length > 0 && (
                  <Card component="button" type="button" className="pressable row-button" aria-expanded={lineupOpen} onClick={() => setLineupOpen(!lineupOpen)}>
                    <Group gap="sm" wrap="nowrap">
                      <Text fw={600}>{t('match.lineup')}</Text>
                      <Text size="sm" c="dimmed">
                        {lineupCount('HOME')} {t('match.vs')} {lineupCount('AWAY')}
                      </Text>
                      <Text size="sm" fw={600} c="teal" ml="auto">
                        {lineupOpen ? t('event.done') : t('event.edit')}
                      </Text>
                    </Group>
                  </Card>
                )}
                {(lineupOpen || !m.lineup.length) && (
                  <LineupEditor
                    key={m.lineup.map((l) => `${l.playerId}${l.side}${l.inGoal}`).join()}
                    match={m}
                    disabled={busy}
                    onSave={(entries) =>
                      action.mutate(() => call(api.PUT('/api/matches/{id}/lineup', { ...path, body: { entries } })) as Promise<Match>, {
                        onSuccess: () => setLineupOpen(false),
                      })
                    }
                  />
                )}
              </>
            ) : (
              <SimpleLineup match={m} />
            )}
            {m.canAdmin && m.status === 'SCHEDULED' && (
              <>
                <Button size="lg" fullWidth variant="default" disabled={busy} onClick={() => setEditing(true)}>
                  {t('match.editDetails')}
                </Button>
                {editing && (
                  <EditDetailsDialog
                    match={m}
                    opened
                    onClose={() => setEditing(false)}
                    onSave={(patch) => run(() => call(api.PATCH('/api/matches/{id}', { ...path, body: { ...version, ...patch } })))}
                  />
                )}
              </>
            )}
            {m.canAdmin && !teamsUndecided(m) && (
              <Stack gap="sm">
                {m.status === 'SCHEDULED' ? (
                  <>
                    {started && (
                      <Button size="lg" fullWidth disabled={busy} onClick={() => setFinishing(true)}>
                        {t('match.finish')}
                      </Button>
                    )}
                    {!m.knockout && (
                      <Button size="lg" fullWidth variant="default" disabled={busy} onClick={() => setCancelling(true)}>
                        {t('match.cancel')}
                      </Button>
                    )}
                    <Modal opened={cancelling} onClose={() => setCancelling(false)} title={t('match.cancel')} centered>
                      <Stack>
                        <Text>{t('match.cancelConfirm')}</Text>
                        <Group justify="flex-end">
                          <Button variant="default" onClick={() => setCancelling(false)}>
                            {t('match.keep')}
                          </Button>
                          <Button
                            color="red"
                            onClick={() => {
                              setCancelling(false)
                              run(() => call(api.POST('/api/matches/{id}/cancel', { ...path, body: version })))
                            }}
                          >
                            {t('match.cancel')}
                          </Button>
                        </Group>
                      </Stack>
                    </Modal>
                    <FinishDialog
                      match={m}
                      opened={finishing}
                      onClose={() => setFinishing(false)}
                      onFinish={(penaltyWinner) =>
                        run(() => call(api.POST('/api/matches/{id}/finish', { ...path, body: { ...version, penaltyWinner } })))
                      }
                    />
                  </>
                ) : (
                  <Button size="lg" fullWidth variant="default" disabled={busy} onClick={() => run(() => call(api.POST('/api/matches/{id}/reopen', { ...path, body: version })))}>
                    {t('match.reopen')}
                  </Button>
                )}
              </Stack>
            )}
          </Stack>
        </div>
        <div className="sticky-col">
          <div style={{ order: 1 }}>
            <EventList match={m} onDelete={editable ? deleteEvent : undefined} disabled={busy} />
          </div>
        </div>
      </div>
    </Stack>
  )
}

function SimpleLineup({ match }: { match: Match }) {
  const { t } = useTranslation()
  if (!match.lineup.length) return null
  const tints = sideTints(match.home.name, match.away.name)
  return (
    <Card>
      <Group align="flex-start" grow>
        {(['HOME', 'AWAY'] as const).map((side) => (
          <Stack key={side} gap={6} data-tint={tints[side]}>
            <Group gap={8} wrap="nowrap">
              <span className="tint-dot" />
              <Text className="section-label" truncate>
                {side === 'HOME' ? match.home.name : match.away.name}
              </Text>
            </Group>
            {match.lineup
              .filter((l) => l.side === side)
              .map((l) => (
                <Anchor key={l.playerId} size="sm" component={Link} to={`/players/${l.playerId}`} display="flex" style={{ alignItems: 'center', gap: 6 }}>
                  {l.name}
                  {l.inGoal && (
                    <>
                      <KeeperIcon size={14} />
                      <VisuallyHidden>({t('match.keeper')})</VisuallyHidden>
                    </>
                  )}
                </Anchor>
              ))}
          </Stack>
        ))}
      </Group>
    </Card>
  )
}
