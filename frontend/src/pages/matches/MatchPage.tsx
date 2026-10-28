import { Alert, Anchor, Button, Divider, Group, Loader, Stack, Text } from '@mantine/core'
import { notifications } from '@mantine/notifications'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useParams } from 'react-router'
import { api, call, type Match } from '../../api/client'
import { errorCode, useNotifyError } from '../../api/errors'
import EventEntry, { type NewEvent } from './EventEntry'
import EventList from './EventList'
import FinishDialog from './FinishDialog'
import LineupEditor from './LineupEditor'
import ScoreHeader from './ScoreHeader'

export default function MatchPage() {
  const { t } = useTranslation()
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
      void queryClient.invalidateQueries({ queryKey: ['matches', 'mine'] })
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

  if (match.isPending) return <Loader />
  if (match.isError) return <Text>{t(`errors.${errorCode(match.error)}`, { defaultValue: t('errors.UNKNOWN') })}</Text>
  const m = match.data
  const version = { version: m.version }
  const run = (fn: () => Promise<unknown>) => action.mutate(fn as () => Promise<Match>)
  // A failed event save stays on screen with a retry, so nothing typed at the pitch is lost (spec 8).
  const addEvent = (e: NewEvent) =>
    action.mutate(() => call(api.POST('/api/matches/{id}/events', { ...path, body: e })) as Promise<Match>, {
      onSuccess: () => setFailedEvent(null),
      onError: () => setFailedEvent(e),
    })
  const playerName = (playerId?: string) => m.lineup.find((l) => l.playerId === playerId)?.name ?? t('event.unknownScorer')
  const deleteEvent = (eventId: string) =>
    run(() => call(api.DELETE('/api/matches/{id}/events/{eventId}', { params: { path: { id, eventId } } })))

  return (
    <Stack maw={700}>
      {m.competitionId && (
        <Anchor component={Link} to={`/competitions/${m.competitionId}`} size="sm">
          {m.competitionName}
          {m.round !== null && ` · ${t('competition.round', { round: m.round })}`}
        </Anchor>
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
      {m.canAdmin && m.status === 'SCHEDULED' && (
        <>
          <EventEntry match={m} onAdd={addEvent} disabled={busy} />
          <Divider />
        </>
      )}
      <EventList match={m} onDelete={m.canAdmin && m.status === 'SCHEDULED' ? deleteEvent : undefined} disabled={busy} />
      <Divider />
      {m.canAdmin && m.status === 'SCHEDULED' ? (
        <LineupEditor
          key={m.lineup.map((l) => `${l.playerId}${l.side}${l.inGoal}`).join()}
          match={m}
          disabled={busy}
          onSave={(entries) => run(() => call(api.PUT('/api/matches/{id}/lineup', { ...path, body: { entries } })))}
        />
      ) : (
        <SimpleLineup match={m} />
      )}
      {m.canAdmin && (
        <Group>
          {m.status === 'SCHEDULED' ? (
            <>
              <Button disabled={busy} onClick={() => setFinishing(true)}>
                {t('match.finish')}
              </Button>
              {!m.knockout && (
                <Button variant="default" disabled={busy} onClick={() => run(() => call(api.POST('/api/matches/{id}/cancel', { ...path, body: version })))}>
                  {t('match.cancel')}
                </Button>
              )}
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
            <Button variant="default" disabled={busy} onClick={() => run(() => call(api.POST('/api/matches/{id}/reopen', { ...path, body: version })))}>
              {t('match.reopen')}
            </Button>
          )}
        </Group>
      )}
    </Stack>
  )
}

function SimpleLineup({ match }: { match: Match }) {
  const { t } = useTranslation()
  if (!match.lineup.length) return null
  return (
    <Group align="flex-start" grow>
      {(['HOME', 'AWAY'] as const).map((side) => (
        <Stack key={side} gap={2}>
          <Text fw={600}>{side === 'HOME' ? match.home.name : match.away.name}</Text>
          {match.lineup
            .filter((l) => l.side === side)
            .map((l) => (
              <Anchor key={l.playerId} size="sm" component={Link} to={`/players/${l.playerId}`}>
                {l.inGoal ? `🧤 ${l.name} (${t('match.keeper')})` : l.name}
              </Anchor>
            ))}
        </Stack>
      ))}
    </Group>
  )
}
