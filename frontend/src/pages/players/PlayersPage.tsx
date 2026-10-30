import { ActionIcon, Anchor, Badge, Button, Card, Group, Stack, TextInput, Title } from '@mantine/core'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { type FormEvent, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { api, call, type Player } from '../../api/client'
import { Link } from 'react-router'
import { fieldErrors, useNotifyError } from '../../api/errors'
import { useAuth } from '../../auth/AuthProvider'
import Crest from '../../components/Crest'
import Empty from '../../components/Empty'
import { PencilIcon, PlusIcon } from '../../components/icons'
import Loading from '../../components/Loading'
import { usePageTitle } from '../../components/usePageTitle'

function PlayerRow({ player }: { player: Player }) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const notifyError = useNotifyError()
  const [editing, setEditing] = useState<string | null>(null)
  const { user, refreshMe } = useAuth()
  const rename = useMutation({
    mutationFn: (name: string) => call(api.PATCH('/api/players/{id}', { params: { path: { id: player.id } }, body: { name } })),
    onSuccess: () => {
      setEditing(null)
      void queryClient.invalidateQueries({ queryKey: ['players', 'mine'] })
      void queryClient.invalidateQueries({ queryKey: ['player', player.id] })
      if (player.id === user?.playerId) void refreshMe()
    },
    onError: notifyError,
  })

  const nameError = fieldErrors(rename.error).name

  if (editing !== null) {
    return (
      <Group
        component="form"
        gap="xs"
        wrap="nowrap"
        py={10}
        onSubmit={(e: FormEvent) => {
          e.preventDefault()
          rename.mutate(editing)
        }}
      >
        <TextInput
          flex={1}
          aria-label={t('players.name')}
          value={editing}
          maxLength={50}
          required
          autoFocus
          onChange={(e) => setEditing(e.currentTarget.value)}
          error={nameError && t(nameError)}
        />
        <Button type="submit" loading={rename.isPending}>
          {t('common.save')}
        </Button>
        <Button variant="subtle" onClick={() => setEditing(null)}>
          {t('common.cancel')}
        </Button>
      </Group>
    )
  }
  return (
    <Group gap="sm" wrap="nowrap" mih={60}>
      <Crest name={player.name} size={36} src={player.imageUrl} />
      <Anchor component={Link} to={`/players/${player.id}`} c="var(--mantine-color-text)" fw={600} flex={1} py={10} truncate>
        {player.name}
      </Anchor>
      {!player.claimed && (
        <Badge color="gray">
          {t('players.guest')}
        </Badge>
      )}
      {player.canEdit && (
        <ActionIcon variant="subtle" color="gray" size={44} radius="xl" aria-label={t('players.rename')} onClick={() => setEditing(player.name)}>
          <PencilIcon size={18} />
        </ActionIcon>
      )}
    </Group>
  )
}

export default function PlayersPage() {
  const { t } = useTranslation()
  usePageTitle(t('players.title'))
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
    <Stack gap="lg">
      <Title order={1}>{t('players.title')}</Title>
      <Group
        component="form"
        align="flex-end"
        gap="xs"
        wrap="nowrap"
        onSubmit={(e: FormEvent) => {
          e.preventDefault()
          create.mutate(name)
        }}
      >
        <TextInput
          flex={1}
          label={t('players.addGuest')}
          placeholder={t('players.name')}
          value={name}
          maxLength={50}
          required
          onChange={(e) => setName(e.currentTarget.value)}
        />
        <Button type="submit" leftSection={<PlusIcon size={18} />} loading={create.isPending}>
          {t('common.add')}
        </Button>
      </Group>
      {mine.isPending ? (
        <Loading />
      ) : mine.data?.length ? (
        <Card py={4} className="list-rows">
          {mine.data.map((p) => (
            <PlayerRow key={p.id} player={p} />
          ))}
        </Card>
      ) : (
        <Empty text={t('teams.emptyRoster')} />
      )}
    </Stack>
  )
}
