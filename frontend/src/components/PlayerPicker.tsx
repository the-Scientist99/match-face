import { MultiSelect } from '@mantine/core'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { api, call, type Player } from '../api/client'
import { showAddGuest } from '../pages/matches/matchLogic'
import { useNotifyError } from '../api/errors'

const NEW = '__new__'

type Props = {
  value: string[]
  onChange: (ids: string[]) => void
  excludeIds?: string[]
  label?: string
}

/** Picks from "my players"; the last option creates a guest named after the search text and selects it. */
export default function PlayerPicker({ value, onChange, excludeIds = [], label }: Props) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const notifyError = useNotifyError()
  const [search, setSearch] = useState('')
  const mine = useQuery({ queryKey: ['players', 'mine'], queryFn: () => call(api.GET('/api/players/mine')) })
  const createGuest = useMutation({
    mutationFn: (name: string) => call(api.POST('/api/players', { body: { name } })),
    onSuccess: (p) => {
      // known to the options before it's selected, so its pill shows the name rather than the id
      queryClient.setQueryData<Player[]>(['players', 'mine'], (old) => [...(old ?? []), p])
      void queryClient.invalidateQueries({ queryKey: ['players', 'mine'] })
      onChange([...value, p.id])
      setSearch('')
    },
    onError: notifyError,
  })

  const name = search.trim()
  const data = (mine.data ?? [])
    .filter((p) => !excludeIds.includes(p.id) || value.includes(p.id))
    .map((p) => ({ value: p.id, label: p.name }))
  if (showAddGuest(name, (mine.data ?? []).map((p) => p.name))) data.push({ value: NEW, label: t('picker.addGuest', { name }) })

  return (
    <MultiSelect
      label={label}
      placeholder={t('picker.placeholder')}
      searchable
      searchValue={search}
      onSearchChange={setSearch}
      data={data}
      value={value}
      disabled={createGuest.isPending}
      onChange={(ids) => (ids.includes(NEW) ? createGuest.mutate(name) : onChange(ids))}
      nothingFoundMessage={t('picker.nothingFound')}
    />
  )
}
