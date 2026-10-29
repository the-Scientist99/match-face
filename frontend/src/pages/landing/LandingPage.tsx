import { Button, Group, Paper, SimpleGrid, Stack, Text, ThemeIcon, Title } from '@mantine/core'
import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import type { Match } from '../../api/client'
import { BallIcon, PersonIcon, TrophyIcon } from '../../components/icons'
import EventList from '../matches/EventList'
import ScoreHeader from '../matches/ScoreHeader'

/** A made-up finished match, drawn with the real match components so the preview always matches the app. */
const SAMPLE: Match = {
  id: 'sample',
  version: 0,
  sport: 'FOOTBALL',
  status: 'FINISHED',
  kickoffAt: null,
  venue: null,
  visibility: 'PRIVATE',
  competitionId: null,
  competitionName: null,
  round: null,
  home: { teamId: null, name: 'Blue Harbour' },
  away: { teamId: null, name: 'Old Town FC' },
  score: { home: 2, away: 1 },
  penaltyWinner: null,
  lineup: [],
  canAdmin: false,
  knockout: false,
  events: [
    { id: 'e1', type: 'GOAL', side: 'HOME', playerId: null, playerName: 'Marko Ilić', assistPlayerId: null, assistPlayerName: 'Luka Perović', minute: 12 },
    { id: 'e2', type: 'YELLOW', side: 'AWAY', playerId: null, playerName: 'Stefan Radović', assistPlayerId: null, assistPlayerName: null, minute: 27 },
    { id: 'e3', type: 'GOAL', side: 'AWAY', playerId: null, playerName: 'Nikola Vuković', assistPlayerId: null, assistPlayerName: null, minute: 34 },
    { id: 'e4', type: 'GOAL', side: 'HOME', playerId: null, playerName: 'Marko Ilić', assistPlayerId: null, assistPlayerName: null, minute: 51 },
  ],
}

function Feature({ icon, title, text }: { icon: ReactNode; title: string; text: string }) {
  return (
    <Paper p="lg">
      <Stack gap="sm">
        <ThemeIcon size={44} radius="md" variant="light">
          {icon}
        </ThemeIcon>
        <Title order={3} fz="lg">
          {title}
        </Title>
        <Text c="dimmed">{text}</Text>
      </Stack>
    </Paper>
  )
}

/** What a logged-out visitor sees at `/`: what the app does, and the way in. */
export default function LandingPage() {
  const { t } = useTranslation()
  return (
    <Stack gap={48} pt={{ base: 'md', md: 48 }}>
      <div className="hero">
        <Stack gap="lg">
          <Text className="section-label" c="teal">
            {t('landing.eyebrow')}
          </Text>
          <Title order={1} fz={{ base: 34, md: 48 }} lh={1.05}>
            {t('landing.title')}
          </Title>
          <Text size="lg" c="dimmed" maw={480}>
            {t('landing.subtitle')}
          </Text>
          <Group gap="xs">
            <Button component={Link} to="/register" size="lg">
              {t('auth.registerTitle')}
            </Button>
            <Button component={Link} to="/login" size="lg" variant="light">
              {t('auth.login')}
            </Button>
          </Group>
        </Stack>
        {/* a picture of the app, not part of the page's content */}
        <Stack gap="sm" aria-hidden>
          <ScoreHeader match={SAMPLE} />
          <EventList match={SAMPLE} />
        </Stack>
      </div>
      <SimpleGrid cols={{ base: 1, sm: 3 }} spacing="md">
        <Feature icon={<BallIcon size={22} />} title={t('landing.liveTitle')} text={t('landing.liveText')} />
        <Feature icon={<TrophyIcon size={22} />} title={t('landing.leaguesTitle')} text={t('landing.leaguesText')} />
        <Feature icon={<PersonIcon size={22} />} title={t('landing.statsTitle')} text={t('landing.statsText')} />
      </SimpleGrid>
    </Stack>
  )
}
