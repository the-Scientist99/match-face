import { ActionIcon, Button, FileButton, Loader, UnstyledButton } from '@mantine/core'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { api, call } from '../api/client'
import { useNotifyError } from '../api/errors'
import { useAuth } from '../auth/AuthProvider'
import Crest, { initials } from './Crest'
import { PencilIcon } from './icons'
import { toSquareJpeg } from './squareImage'

type Owner = { kind: 'players' | 'teams' | 'competitions'; id: string }

/** Uploads (a file) or removes (null) the image of a player, team or competition. */
function useImage({ kind, id }: Owner) {
  const queryClient = useQueryClient()
  const notifyError = useNotifyError()
  const { refreshMe } = useAuth()
  // the three endpoints have the same shape; one literal path keeps openapi-fetch's typing simple
  const path = `/api/${kind}/{id}/image` as '/api/teams/{id}/image'
  const params = { params: { path: { id } } }
  return useMutation({
    mutationFn: async (file: File | null) => {
      if (!file) return call(api.DELETE(path, params))
      const blob = await toSquareJpeg(file)
      return call(
        api.PUT(path, {
          ...params,
          body: { file: '' }, // the real body is the form below
          bodySerializer: () => {
            const form = new FormData()
            form.append('file', blob, 'image.jpg')
            return form
          },
        }),
      )
    },
    // a logo shows on match cards, lists and pages alike: refetch whatever is on screen,
    // and the header avatar when it may be your own photo
    onSuccess: () => Promise.all([queryClient.invalidateQueries(), kind === 'players' && refreshMe()]),
    onError: notifyError,
  })
}

/** The crest for someone who may edit it: tapping it picks a new image; a pencil badge says so. */
export default function EditableCrest({ name, imageUrl, size, ...owner }: Owner & { name: string; imageUrl: string | null; size: number }) {
  const { t } = useTranslation()
  const save = useImage(owner)
  return (
    <FileButton accept="image/*" disabled={save.isPending} onChange={(file) => file && save.mutate(file)}>
      {(props) => (
        <UnstyledButton {...props} className="crest-edit pressable" aria-label={imageUrl ? t('image.change') : t('image.add')}>
          <Crest name={name} src={imageUrl} size={size} />
          <span className="crest-edit-badge" aria-hidden>
            {save.isPending ? <Loader size={12} color="white" /> : <PencilIcon size={14} />}
          </span>
        </UnstyledButton>
      )}
    </FileButton>
  )
}

/** A large photo for a profile, with the same picker on a button in its corner for someone who may edit it. */
export function PlayerPhoto({ name, imageUrl, canEdit, ...owner }: Owner & { name: string; imageUrl: string | null; canEdit: boolean }) {
  const { t } = useTranslation()
  const save = useImage(owner)
  return (
    <div className="player-photo">
      {imageUrl ? (
        <img src={imageUrl} alt={name} />
      ) : (
        <span className="player-photo-initials" aria-hidden>
          {initials(name)}
        </span>
      )}
      {canEdit && (
        <FileButton accept="image/*" disabled={save.isPending} onChange={(file) => file && save.mutate(file)}>
          {(props) => (
            <ActionIcon {...props} className="player-photo-edit" variant="white" color="dark" radius="xl" size={44} loading={save.isPending} aria-label={imageUrl ? t('image.change') : t('image.add')}>
              <PencilIcon size={20} />
            </ActionIcon>
          )}
        </FileButton>
      )}
    </div>
  )
}

/** A quiet link to remove the image; kept apart from the crest so a stray tap can't delete it. */
export function RemoveImageButton(owner: Owner) {
  const { t } = useTranslation()
  const save = useImage(owner)
  return (
    <Button size="sm" px="xs" variant="subtle" color="gray" loading={save.isPending} onClick={() => save.mutate(null)}>
      {t('image.remove')}
    </Button>
  )
}
