import { ActionIcon, Badge, Button, Group, Loader, Stack, Text, TextInput, Title } from '@mantine/core'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { type FormEvent, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { api, call, type Player } from '../../api/client'
import { fieldErrors, useNotifyError } from '../../api/errors'

function PlayerRow({ player }: { player: Player }) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const notifyError = useNotifyError()
  const [editing, setEditing] = useState<string | null>(null)
  const rename = useMutation({
    mutationFn: (name: string) => call(api.PATCH('/api/players/{id}', { params: { path: { id: player.id } }, body: { name } })),
    onSuccess: () => {
      setEditing(null)
      void queryClient.invalidateQueries({ queryKey: ['players', 'mine'] })
    },
    onError: notifyError,
  })

  const nameError = fieldErrors(rename.error).name

  if (editing !== null) {
    return (
      <Group
        component="form"
        gap="xs"
        onSubmit={(e: FormEvent) => {
          e.preventDefault()
          rename.mutate(editing)
        }}
      >
        <TextInput
          aria-label={t('players.name')}
          value={editing}
          maxLength={50}
          required
          autoFocus
          onChange={(e) => setEditing(e.currentTarget.value)}
          error={nameError && t(nameError)}
        />
        <Button type="submit" size="xs" loading={rename.isPending}>
          {t('common.save')}
        </Button>
        <Button size="xs" variant="subtle" onClick={() => setEditing(null)}>
          {t('common.cancel')}
        </Button>
      </Group>
    )
  }
  return (
    <Group gap="xs">
      <Text>{player.name}</Text>
      {!player.claimed && (
        <Badge size="sm" variant="light">
          {t('players.guest')}
        </Badge>
      )}
      {player.canEdit && (
        <ActionIcon variant="subtle" size="sm" aria-label={t('players.rename')} onClick={() => setEditing(player.name)}>
          ✎
        </ActionIcon>
      )}
    </Group>
  )
}

export default function PlayersPage() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const notifyError = useNotifyError()
  const [name, setName] = useState('')
  const mine = useQuery({ queryKey: ['players', 'mine'], queryFn: () => call(api.GET('/api/players/mine')) })
  const create = useMutation({
    mutationFn: (n: string) => call(api.POST('/api/players', { body: { name: n } })),
    onSuccess: () => {
      setName('')
      void queryClient.invalidateQueries({ queryKey: ['players', 'mine'] })
    },
    onError: notifyError,
  })

  return (
    <Stack maw={600}>
      <Title order={2}>{t('players.title')}</Title>
      <Group
        component="form"
        align="flex-end"
        gap="xs"
        onSubmit={(e: FormEvent) => {
          e.preventDefault()
          create.mutate(name)
        }}
      >
        <TextInput
          label={t('players.addGuest')}
          placeholder={t('players.name')}
          value={name}
          maxLength={50}
          required
          onChange={(e) => setName(e.currentTarget.value)}
        />
        <Button type="submit" loading={create.isPending}>
          {t('common.add')}
        </Button>
      </Group>
      {mine.isPending ? <Loader /> : mine.data?.map((p) => <PlayerRow key={p.id} player={p} />)}
    </Stack>
  )
}
