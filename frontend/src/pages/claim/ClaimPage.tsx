import { Button, Group, Loader, Stack, Text, Title } from '@mantine/core'
import { notifications } from '@mantine/notifications'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Link, useNavigate, useParams } from 'react-router'
import { api, call } from '../../api/client'
import { errorCode, useNotifyError } from '../../api/errors'
import { useAuth } from '../../auth/AuthProvider'

/** `/claim/:token`: preview the guest's history, then (logged in) merge it into your own player. */
export default function ClaimPage() {
  const { t } = useTranslation()
  const { token = '' } = useParams()
  const { user, ready } = useAuth()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const notifyError = useNotifyError()
  const preview = useQuery({
    queryKey: ['claim', token],
    queryFn: () => call(api.GET('/api/players/claims/{token}', { params: { path: { token } } })),
    retry: false,
  })
  const claim = useMutation({
    mutationFn: () => call(api.POST('/api/players/claims/{token}', { params: { path: { token } } })),
    onSuccess: (r) => {
      queryClient.removeQueries({ queryKey: ['claim', token] })
      void queryClient.invalidateQueries({ queryKey: ['players'] })
      void queryClient.invalidateQueries({ queryKey: ['player'] })
      void queryClient.invalidateQueries({ queryKey: ['matches', 'mine'] })
      notifications.show({ color: 'green', message: t('claim.done') })
      navigate(`/players/${r.playerId}`)
    },
    onError: notifyError,
  })
  const next = encodeURIComponent(`/claim/${token}`)

  if (preview.isPending || !ready) return <Loader />
  if (preview.isError) return <Text>{t(`errors.${errorCode(preview.error)}`, { defaultValue: t('errors.UNKNOWN') })}</Text>
  const p = preview.data
  return (
    <Stack maw={500} w="100%" mx="auto" className="surface">
      <Title order={2}>{t('claim.title', { name: p.playerName })}</Title>
      <Text>{t('claim.summary', { matches: p.appearances, goals: p.goals })}</Text>
      {user ? (
        <Group>
          <Button loading={claim.isPending} onClick={() => claim.mutate()}>
            {t('claim.confirm')}
          </Button>
        </Group>
      ) : (
        <>
          <Text size="sm" c="dimmed">
            {t('claim.loginFirst')}
          </Text>
          <Group>
            <Button component={Link} to={`/login?next=${next}`}>
              {t('auth.login')}
            </Button>
            <Button component={Link} to={`/register?next=${next}`} variant="light">
              {t('auth.register')}
            </Button>
          </Group>
        </>
      )}
    </Stack>
  )
}
