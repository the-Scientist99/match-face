import { Button, Group, Modal, Stack, Text } from '@mantine/core'
import { useTranslation } from 'react-i18next'
import type { Match } from '../../api/client'
import { needsPenalties } from './matchLogic'

type Side = 'HOME' | 'AWAY'

/** Confirms finishing; a level cup match first asks who won the penalty shootout. */
export default function FinishDialog({ match, opened, onClose, onFinish }: {
  match: Match
  opened: boolean
  onClose: () => void
  onFinish: (penaltyWinner?: Side) => void
}) {
  const { t } = useTranslation()
  const penalties = needsPenalties(match)
  const finish = (side?: Side) => {
    onFinish(side)
    onClose()
  }
  return (
    <Modal opened={opened} onClose={onClose} title={t('match.finish')} centered>
      <Stack>
        <Text>{penalties ? t('match.whoWonPenalties') : t('match.finishConfirm', { home: match.score.home, away: match.score.away })}</Text>
        <Group justify="flex-end">
          <Button variant="default" onClick={onClose}>
            {t('common.cancel')}
          </Button>
          {penalties ? (
            <>
              <Button onClick={() => finish('HOME')}>{match.home.name}</Button>
              <Button onClick={() => finish('AWAY')}>{match.away.name}</Button>
            </>
          ) : (
            <Button onClick={() => finish()}>{t('match.finish')}</Button>
          )}
        </Group>
      </Stack>
    </Modal>
  )
}
