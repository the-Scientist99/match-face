import { Anchor, Button, Group, Select, Stack, Text, Title } from '@mantine/core'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { api, call, type Competition } from '../../api/client'
import { useNotifyError } from '../../api/errors'
import { useAuth } from '../../auth/AuthProvider'
import { useLoginPath } from '../../auth/RequireAuth'

/** For a team's admin: asks to join a competition with open entries, and shows the requests still waiting. */
export default function EnterTeam({ competition }: { competition: Competition }) {
  const { t } = useTranslation()
  const { user } = useAuth()
  const loginPath = useLoginPath()
  const queryClient = useQueryClient()
  const notifyError = useNotifyError()
  const [teamId, setTeamId] = useState<string | null>(null)
  const id = competition.id
  const teams = useQuery({ queryKey: ['teams', 'mine'], queryFn: () => call(api.GET('/api/teams/mine')), enabled: !!user })
  const taken = new Set([...competition.teams, ...competition.entries].map((team) => team.teamId))
  const options = (teams.data ?? []).filter((team) => team.canAdmin && !taken.has(team.id)).map((team) => ({ value: team.id, label: team.name }))

  const action = useMutation({
    mutationFn: (run: () => Promise<Competition>) => run(),
    onSuccess: (updated) => {
      queryClient.setQueryData(['competition', id], updated)
      void queryClient.invalidateQueries({ queryKey: ['competition', id] })
      setTeamId(null)
    },
    onError: notifyError,
  })
  const enter = (team: string) => action.mutate(() => call(api.POST('/api/competitions/{id}/entries', { params: { path: { id } }, body: { teamId: team } })))
  const withdraw = (team: string) =>
    action.mutate(() => call(api.DELETE('/api/competitions/{id}/entries/{teamId}', { params: { path: { id, teamId: team } } })))

  if (!user) {
    return (
      <Button variant="light" component={Link} to={loginPath}>
        {t('competition.logInToEnter')}
      </Button>
    )
  }

  return (
    <Stack className="surface">
      <Title order={5}>{t('competition.entriesTitle')}</Title>
      {competition.entries.map((entry) => (
        <Group key={entry.teamId} justify="space-between">
          <div>
            <Text>{entry.name}</Text>
            <Text size="sm" c="dimmed">
              {t('competition.entryPending')}
            </Text>
          </div>
          <Button variant="subtle" color="red" disabled={action.isPending} onClick={() => withdraw(entry.teamId)}>
            {t('competition.withdraw')}
          </Button>
        </Group>
      ))}
      {options.length > 0 ? (
        <Group align="flex-end" gap="xs">
          <Select label={t('competition.enterTeam')} data={options} value={teamId} onChange={setTeamId} flex={1} />
          <Button disabled={!teamId} loading={action.isPending} onClick={() => teamId && enter(teamId)}>
            {t('competition.enter')}
          </Button>
        </Group>
      ) : (
        teams.isSuccess &&
        !competition.entries.length && (
          <Text size="sm" c="dimmed">
            {t('competition.needTeam')}{' '}
            <Anchor component={Link} to="/teams" size="sm">
              {t('nav.teams')}
            </Anchor>
          </Text>
        )
      )}
    </Stack>
  )
}
