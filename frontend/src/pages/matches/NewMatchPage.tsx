import { Button, Group, NumberInput, SegmentedControl, Select, Stack, TextInput, Title } from '@mantine/core'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { type FormEvent, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router'
import { api, call } from '../../api/client'
import { useNotifyError } from '../../api/errors'
import { isoToLocal, localToIso } from './matchLogic'

type Kind = 'pickup' | 'teams'
type Visibility = 'PRIVATE' | 'PUBLIC'

export default function NewMatchPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const notifyError = useNotifyError()
  const [kind, setKind] = useState<Kind>('pickup')
  const [homeLabel, setHomeLabel] = useState(() => t('match.defaultHome'))
  const [awayLabel, setAwayLabel] = useState(() => t('match.defaultAway'))
  const [homeTeamId, setHomeTeamId] = useState<string | null>(null)
  const [awayTeamId, setAwayTeamId] = useState<string | null>(null)
  const [kickoff, setKickoff] = useState('')
  const [now] = useState(() => isoToLocal(new Date().toISOString())) // the picker's earliest choice; the server checks too
  const [venue, setVenue] = useState('')
  const [visibility, setVisibility] = useState<Visibility>('PRIVATE')
  const [maxPerSide, setMaxPerSide] = useState<number | string>('')
  const teams = useQuery({ queryKey: ['teams', 'mine'], queryFn: () => call(api.GET('/api/teams/mine')), enabled: kind === 'teams' })
  const teamOptions = (teams.data ?? []).filter((team) => team.canAdmin).map((team) => ({ value: team.id, label: team.name }))

  const create = useMutation({
    mutationFn: () =>
      call(
        api.POST('/api/matches', {
          body: {
            ...(kind === 'pickup'
              ? { homeLabel, awayLabel, maxPerSide: typeof maxPerSide === 'number' ? maxPerSide : undefined }
              : { homeTeamId: homeTeamId ?? undefined, awayTeamId: awayTeamId ?? undefined }),
            kickoffAt: localToIso(kickoff)!, // the input is required, so never empty here
            venue: venue || undefined,
            visibility,
          },
        }),
      ),
    onSuccess: (match) => {
      queryClient.setQueryData(['match', match.id], match)
      void queryClient.invalidateQueries({ queryKey: ['matches'] })
      navigate(`/matches/${match.id}`)
    },
    onError: notifyError,
  })

  return (
    <Stack
      component="form"
      maw={500}
      w="100%"
      mx="auto"
      className="surface"
      onSubmit={(e: FormEvent) => {
        e.preventDefault()
        create.mutate()
      }}
    >
      <Title order={2}>{t('match.new')}</Title>
      <SegmentedControl
        value={kind}
        onChange={(v) => setKind(v as Kind)}
        data={[
          { value: 'pickup', label: t('match.pickup') },
          { value: 'teams', label: t('match.teams') },
        ]}
      />
      {kind === 'pickup' ? (
        <Group grow>
          <TextInput label={t('match.home')} value={homeLabel} maxLength={30} required onChange={(e) => setHomeLabel(e.currentTarget.value)} />
          <TextInput label={t('match.away')} value={awayLabel} maxLength={30} required onChange={(e) => setAwayLabel(e.currentTarget.value)} />
        </Group>
      ) : (
        <Group grow>
          <Select label={t('match.home')} data={teamOptions} value={homeTeamId} onChange={setHomeTeamId} required searchable />
          <Select label={t('match.away')} data={teamOptions} value={awayTeamId} onChange={setAwayTeamId} required searchable />
        </Group>
      )}
      <TextInput
        label={t('match.kickoff')}
        type="datetime-local"
        required
        min={now}
        value={kickoff}
        onChange={(e) => setKickoff(e.currentTarget.value)}
      />
      <TextInput label={t('match.venue')} value={venue} maxLength={100} onChange={(e) => setVenue(e.currentTarget.value)} />
      {kind === 'pickup' && (
        <NumberInput
          label={t('match.maxPerSide')}
          description={t('match.maxPerSideHint')}
          min={1}
          max={30}
          allowDecimal={false}
          value={maxPerSide}
          onChange={setMaxPerSide}
        />
      )}
      <SegmentedControl
        value={visibility}
        onChange={(v) => setVisibility(v as Visibility)}
        data={[
          { value: 'PRIVATE', label: t('match.visibility.PRIVATE') },
          { value: 'PUBLIC', label: t('match.visibility.PUBLIC') },
        ]}
      />
      <Button type="submit" loading={create.isPending}>
        {t('match.create')}
      </Button>
    </Stack>
  )
}
