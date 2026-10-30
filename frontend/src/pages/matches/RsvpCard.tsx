import { Button, Card, Group, Stack, Text } from '@mantine/core'
import { useTranslation } from 'react-i18next'
import type { Match } from '../../api/client'

/**
 * "Are you playing?" for roster players of a team match, and everyone's answers by side. Hidden when
 * nobody can answer and nobody has.
 */
export default function RsvpCard({ match, onAnswer, disabled }: { match: Match; onAnswer: (going: boolean) => void; disabled: boolean }) {
  const { t } = useTranslation()
  if (!match.canRsvp && !match.rsvps.length) return null
  const names = (side: 'HOME' | 'AWAY', going: boolean) =>
    match.rsvps.filter((r) => r.side === side && r.going === going).map((r) => r.name)
  return (
    <Card>
      <Stack gap="sm">
        <h2 className="section-title">{t('rsvp.title')}</h2>
        {match.canRsvp && (
          <Group grow>
            <Button disabled={disabled} variant={match.myRsvp === true ? 'filled' : 'default'} aria-pressed={match.myRsvp === true} onClick={() => onAnswer(true)}>
              {t('rsvp.in')}
            </Button>
            <Button disabled={disabled} variant={match.myRsvp === false ? 'filled' : 'default'} color={match.myRsvp === false ? 'gray' : undefined} aria-pressed={match.myRsvp === false} onClick={() => onAnswer(false)}>
              {t('rsvp.out')}
            </Button>
          </Group>
        )}
        {(['HOME', 'AWAY'] as const).map((side) => {
          const inNames = names(side, true)
          const outNames = names(side, false)
          if (!inNames.length && !outNames.length) return null
          return (
            <Stack key={side} gap={2}>
              <Text className="section-label">{side === 'HOME' ? match.home.name : match.away.name}</Text>
              {inNames.length > 0 && <Text size="sm">{t('rsvp.inList', { count: inNames.length, names: inNames.join(', ') })}</Text>}
              {outNames.length > 0 && (
                <Text size="sm" c="dimmed">
                  {t('rsvp.outList', { count: outNames.length, names: outNames.join(', ') })}
                </Text>
              )}
            </Stack>
          )
        })}
      </Stack>
    </Card>
  )
}
