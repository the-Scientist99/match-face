import { Button, CopyButton, Modal, Stack, Text, TextInput } from '@mantine/core'
import { useMutation } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { api, call } from '../api/client'
import { useNotifyError } from '../api/errors'
import { htmlLang, type Lang } from '../lang'

/** Creates a claim invite for a guest and shows the one-time link to send to the player. */
export default function InviteButton({ playerId }: { playerId: string }) {
  const { t, i18n } = useTranslation()
  const notifyError = useNotifyError()
  const invite = useMutation({
    mutationFn: () => call(api.POST('/api/players/{id}/claims', { params: { path: { id: playerId } } })),
    onError: notifyError,
  })
  const link = invite.data ? `${window.location.origin}/claim/${invite.data.token}` : ''

  return (
    <>
      <Button variant="light" loading={invite.isPending} onClick={() => invite.mutate()}>
        {t('claim.invite')}
      </Button>
      <Modal opened={!!invite.data} onClose={() => invite.reset()} title={t('claim.invite')} centered>
        <Stack>
          <Text size="sm">{t('claim.shareHint')}</Text>
          <TextInput value={link} readOnly onFocus={(e) => e.currentTarget.select()} />
          <CopyButton value={link}>
            {({ copied, copy }) => (
              <Button color={copied ? 'green' : undefined} onClick={copy}>
                {copied ? t('claim.copied') : t('claim.copy')}
              </Button>
            )}
          </CopyButton>
          {invite.data && (
            <Text size="xs" c="dimmed">
              {t('claim.expires', { date: new Date(invite.data.expiresAt).toLocaleDateString(htmlLang(i18n.language as Lang)) })}
            </Text>
          )}
        </Stack>
      </Modal>
    </>
  )
}
