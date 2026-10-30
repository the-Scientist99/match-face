import { Button, Group, Modal, SegmentedControl, Stack, TextInput } from '@mantine/core'
import { type FormEvent, useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { Match } from '../../api/client'
import { isoToLocal, localToIso } from './matchLogic'

export type DetailsPatch = { kickoffAt?: string; venue: string; visibility?: 'PRIVATE' | 'PUBLIC' }

/**
 * Kickoff, venue and (one-off matches only) visibility. The kickoff is sent only when it changed, so a
 * match whose kickoff has passed can still get a new venue.
 */
export default function EditDetailsDialog({ match, opened, onClose, onSave }: {
  match: Match
  opened: boolean
  onClose: () => void
  onSave: (patch: DetailsPatch) => void
}) {
  const { t } = useTranslation()
  const [kickoff, setKickoff] = useState(() => isoToLocal(match.kickoffAt))
  const [venue, setVenue] = useState(match.venue ?? '')
  const [visibility, setVisibility] = useState(match.visibility ?? 'PRIVATE')
  const [now] = useState(() => isoToLocal(new Date().toISOString()))
  const kickoffChanged = kickoff !== isoToLocal(match.kickoffAt)

  return (
    <Modal opened={opened} onClose={onClose} title={t('match.editDetails')} centered>
      <Stack
        component="form"
        onSubmit={(e: FormEvent) => {
          e.preventDefault()
          onSave({
            kickoffAt: kickoffChanged ? localToIso(kickoff) : undefined,
            venue,
            visibility: match.competitionId ? undefined : visibility,
          })
          onClose()
        }}
      >
        <TextInput
          label={t('match.kickoff')}
          type="datetime-local"
          required={kickoffChanged || !!match.kickoffAt}
          min={kickoffChanged ? now : undefined}
          value={kickoff}
          onChange={(e) => setKickoff(e.currentTarget.value)}
        />
        <TextInput label={t('match.venue')} value={venue} maxLength={100} onChange={(e) => setVenue(e.currentTarget.value)} />
        {!match.competitionId && (
          <SegmentedControl
            value={visibility}
            onChange={(v) => setVisibility(v as 'PRIVATE' | 'PUBLIC')}
            data={[
              { value: 'PRIVATE', label: t('match.visibility.PRIVATE') },
              { value: 'PUBLIC', label: t('match.visibility.PUBLIC') },
            ]}
          />
        )}
        <Group justify="flex-end">
          <Button variant="default" onClick={onClose}>
            {t('common.cancel')}
          </Button>
          <Button type="submit">{t('common.save')}</Button>
        </Group>
      </Stack>
    </Modal>
  )
}
