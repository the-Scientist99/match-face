import { Button, Group, Loader, Modal, Stack, Text, TextInput, Title } from '@mantine/core'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { type FormEvent, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { api, call, type MatchSummary } from '../../api/client'
import { useNotifyError } from '../../api/errors'
import MatchList from '../../components/MatchList'
import { groupByRound, isoToLocal, localToIso } from '../matches/matchLogic'

/** One date and time for every still-scheduled match of a round, e.g. a weekly matchday. */
function RoundDateDialog({ id, round, onClose }: { id: string; round: number; onClose: () => void }) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const notifyError = useNotifyError()
  const [kickoff, setKickoff] = useState('')
  const [now] = useState(() => isoToLocal(new Date().toISOString()))
  const save = useMutation({
    mutationFn: () =>
      call(
        api.PUT('/api/competitions/{id}/rounds/{round}/kickoff', {
          params: { path: { id, round } },
          body: { kickoffAt: localToIso(kickoff)! }, // the input is required
        }),
      ),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['competition', id] })
      void queryClient.invalidateQueries({ queryKey: ['matches'] })
      void queryClient.invalidateQueries({ queryKey: ['match'] })
      onClose()
    },
    onError: notifyError,
  })
  return (
    <Modal opened onClose={onClose} title={t('competition.roundDate', { round })} centered>
      <Stack
        component="form"
        onSubmit={(e: FormEvent) => {
          e.preventDefault()
          save.mutate()
        }}
      >
        <Text size="sm" c="dimmed">
          {t('competition.roundDateHint')}
        </Text>
        <TextInput label={t('match.kickoff')} type="datetime-local" required min={now} value={kickoff} onChange={(e) => setKickoff(e.currentTarget.value)} />
        <Group justify="flex-end">
          <Button variant="default" onClick={onClose}>
            {t('common.cancel')}
          </Button>
          <Button type="submit" loading={save.isPending}>
            {t('common.save')}
          </Button>
        </Group>
      </Stack>
    </Modal>
  )
}

export default function FixturesTab({ id, canAdmin }: { id: string; canAdmin: boolean }) {
  const { t } = useTranslation()
  const [dating, setDating] = useState<number | null>(null)
  const matches = useQuery<MatchSummary[]>({
    queryKey: ['competition', id, 'matches'],
    queryFn: () => call(api.GET('/api/competitions/{id}/matches', { params: { path: { id } } })),
  })
  if (matches.isPending) return <Loader />
  if (!matches.data?.length) return <Text c="dimmed">{t('competition.noFixtures')}</Text>
  return (
    <Stack>
      {groupByRound(matches.data).map(([round, ms]) => (
        <Stack key={round ?? 'none'} gap="xs">
          <Group justify="space-between">
            <Title order={5}>{round !== null ? t('competition.round', { round }) : t('competition.unscheduled')}</Title>
            {canAdmin && round !== null && ms.some((m) => m.status === 'SCHEDULED') && (
              <Button size="compact-sm" variant="subtle" onClick={() => setDating(round)}>
                {t('competition.setDate')}
              </Button>
            )}
          </Group>
          <MatchList matches={ms} />
        </Stack>
      ))}
      {dating !== null && <RoundDateDialog id={id} round={dating} onClose={() => setDating(null)} />}
    </Stack>
  )
}
