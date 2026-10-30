import { Group, Stack, Text, VisuallyHidden } from '@mantine/core'
import { useTranslation } from 'react-i18next'
import type { Match, SideView } from '../../api/client'
import Crest, { sideTints, type Tint } from '../../components/Crest'
import { formatKickoff } from '../../components/MatchList'
import PitchLines from '../../components/PitchLines'
import { isLive } from './matchLogic'

/** The match's dark board once it has kicked off: the score large, the live state in words as well as a dot. */
export default function ScoreHeader({ match }: { match: Match }) {
  const { t, i18n } = useTranslation()
  const details = [formatKickoff(match.kickoffAt, i18n.language), match.venue].filter(Boolean).join(' · ')
  const tints = sideTints(match.home.name, match.away.name)
  const live = isLive(match)
  const home = match.home.name ?? t('bracket.tbd')
  const away = match.away.name ?? t('bracket.tbd')
  const side = ({ name, imageUrl }: SideView, tint: Tint) => (
    <Stack gap={10} align="center" flex={1} miw={0}>
      <Crest name={name} tint={tint} size={72} src={imageUrl} />
      <Text component="div" fw={600} size="lg" ta="center" lh={1.25} lineClamp={2} c={name ? undefined : 'dimmed'}>
        {name ?? t('bracket.tbd')}
      </Text>
    </Stack>
  )
  return (
    <section className="board" aria-labelledby="match-title">
      <PitchLines />
      <Stack gap="lg" align="center">
        <VisuallyHidden component="h1" id="match-title">
          {home} {t('match.vs')} {away}
        </VisuallyHidden>
        <Group wrap="nowrap" gap="xs" w="100%">
          {side(match.home, tints.HOME)}
          <div className="score score-big" aria-hidden>
            {match.score.home}
            <span className="score-sep">–</span>
            {match.score.away}
          </div>
          {side(match.away, tints.AWAY)}
        </Group>
        <VisuallyHidden aria-live="polite">
          {home} {match.score.home}, {away} {match.score.away}
        </VisuallyHidden>
        <Group gap="sm" justify="center">
          <span className="status-chip">
            {live && <span className="live-dot" aria-hidden />}
            {t(`match.status.${live ? 'LIVE' : match.status}`)}
          </span>
          {details && (
            <Text size="sm" c="dimmed" fw={500}>
              {details}
            </Text>
          )}
        </Group>
      </Stack>
    </section>
  )
}
