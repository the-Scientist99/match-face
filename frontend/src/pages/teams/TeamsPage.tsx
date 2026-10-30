import { Button, Card, Group, Stack, Text, TextInput, Title } from '@mantine/core'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { type FormEvent, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useNavigate } from 'react-router'
import { api, call } from '../../api/client'
import { useNotifyError } from '../../api/errors'
import Crest from '../../components/Crest'
import Empty from '../../components/Empty'
import { ChevronIcon, PlusIcon } from '../../components/icons'
import Loading from '../../components/Loading'
import { usePageTitle } from '../../components/usePageTitle'

export default function TeamsPage() {
  const { t } = useTranslation()
  usePageTitle(t('teams.title'))
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
    <Stack gap="lg">
      <Title order={1}>{t('teams.title')}</Title>
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
          label={t('teams.create')}
          placeholder={t('teams.name')}
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
        <Card p={0} className="list-rows">
          {mine.data.map((team) => (
            <Link key={team.id} to={`/teams/${team.id}`} className="list-link">
              <Group gap="sm" wrap="nowrap" mih={64} px="md">
                <Crest name={team.name} size={40} src={team.imageUrl} />
                <Text fw={600} flex={1} truncate>
                  {team.name}
                </Text>
                <Text c="dimmed" component="span" display="flex">
                  <ChevronIcon size={18} />
                </Text>
              </Group>
            </Link>
          ))}
        </Card>
      ) : (
        <Empty text={t('teams.none')} />
      )}
    </Stack>
  )
}
