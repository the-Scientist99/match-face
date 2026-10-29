import { Button, Checkbox, SegmentedControl, Stack, TextInput, Title } from '@mantine/core'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { type FormEvent, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router'
import { api, call } from '../../api/client'
import { useNotifyError } from '../../api/errors'

type Visibility = 'PRIVATE' | 'PUBLIC'

export default function NewCompetitionPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const notifyError = useNotifyError()
  const [name, setName] = useState('')
  const [visibility, setVisibility] = useState<Visibility>('PRIVATE')
  const [doubleRoundRobin, setDoubleRoundRobin] = useState(false)
  const [type, setType] = useState<'LEAGUE' | 'CUP'>('LEAGUE')
  const create = useMutation({
    mutationFn: () => call(api.POST('/api/competitions', { body: { name, type, visibility, doubleRoundRobin: type === 'LEAGUE' && doubleRoundRobin } })),
    onSuccess: (c) => {
      void queryClient.invalidateQueries({ queryKey: ['competitions', 'mine'] })
      navigate(`/competitions/${c.id}?tab=manage`)
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
      <Title order={2}>{t('competition.new')}</Title>
      <SegmentedControl
        value={type}
        onChange={(v) => setType(v as 'LEAGUE' | 'CUP')}
        data={[
          { value: 'LEAGUE', label: t('competition.type.LEAGUE') },
          { value: 'CUP', label: t('competition.type.CUP') },
        ]}
      />
      <TextInput label={t('competition.name')} value={name} maxLength={80} required onChange={(e) => setName(e.currentTarget.value)} />
      <SegmentedControl
        value={visibility}
        onChange={(v) => setVisibility(v as Visibility)}
        data={[
          { value: 'PRIVATE', label: t('match.visibility.PRIVATE') },
          { value: 'PUBLIC', label: t('match.visibility.PUBLIC') },
        ]}
      />
      {type === 'LEAGUE' && (
        <Checkbox label={t('competition.doubleRoundRobin')} checked={doubleRoundRobin} onChange={(e) => setDoubleRoundRobin(e.currentTarget.checked)} />
      )}
      <Button type="submit" loading={create.isPending}>
        {t('competition.create')}
      </Button>
    </Stack>
  )
}
