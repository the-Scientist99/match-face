import { Anchor, Button, Group, Loader, Stack, Text, TextInput, Title } from '@mantine/core'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { type FormEvent, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useNavigate } from 'react-router'
import { api, call } from '../../api/client'
import { useNotifyError } from '../../api/errors'

export default function TeamsPage() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const notifyError = useNotifyError()
  const [name, setName] = useState('')
  const mine = useQuery({ queryKey: ['teams', 'mine'], queryFn: () => call(api.GET('/api/teams/mine')) })
  const create = useMutation({
    mutationFn: (n: string) => call(api.POST('/api/teams', { body: { name: n } })),
    onSuccess: (team) => {
      void queryClient.invalidateQueries({ queryKey: ['teams', 'mine'] })
      navigate(`/teams/${team.id}`)
    },
    onError: notifyError,
  })

  return (
    <Stack maw={600}>
      <Title order={2}>{t('teams.title')}</Title>
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
          label={t('teams.create')}
          placeholder={t('teams.name')}
          value={name}
          maxLength={50}
          required
          onChange={(e) => setName(e.currentTarget.value)}
        />
        <Button type="submit" loading={create.isPending}>
          {t('common.add')}
        </Button>
      </Group>
      {mine.isPending ? (
        <Loader />
      ) : mine.data?.length ? (
        mine.data.map((team) => (
          <Anchor key={team.id} component={Link} to={`/teams/${team.id}`}>
            {team.name}
          </Anchor>
        ))
      ) : (
        <Text c="dimmed">{t('teams.none')}</Text>
      )}
    </Stack>
  )
}
