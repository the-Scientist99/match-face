import { Button, Group, Paper, Stack, Text, Title } from '@mantine/core'
import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { CardIcon } from '../../components/icons'
import Crest from '../../components/Crest'
import PitchLines from '../../components/PitchLines'
import { usePageTitle } from '../../components/usePageTitle'

/** A made-up match, league and player: pictures of the app, not part of the page's content. */
const EVENTS = [
  { minute: 12, player: 'Marko Ilić', type: 'GOAL' },
  { minute: 27, player: 'Stefan Radović', type: 'YELLOW' },
  { minute: 34, player: 'Nikola Vuković', type: 'GOAL' },
  { minute: 51, player: 'Marko Ilić', type: 'GOAL' },
]
const TABLE = [
  ['Blue Harbour', 6, 14],
  ['Old Town FC', 6, 12],
  ['Lovćen Boys', 6, 10],
  ['Northside United', 6, 8],
] as const

function HeroPreview() {
  const { t } = useTranslation()
  return (
    <Paper p="xl" aria-hidden className="on-light">
      <Stack gap="lg">
        <Group wrap="nowrap" justify="space-between" gap="sm">
          <Stack gap={8} align="center" flex={1} miw={0}>
            <Crest name="Blue Harbour" tint="blue" size={56} />
            <Text fw={600} ta="center" lh={1.25}>
              Blue Harbour
            </Text>
          </Stack>
          <Text component="div" className="score" fz={72} lh={1}>
            2–1
          </Text>
          <Stack gap={8} align="center" flex={1} miw={0}>
            <Crest name="Old Town FC" tint="orange" size={56} />
            <Text fw={600} ta="center" lh={1.25}>
              Old Town FC
            </Text>
          </Stack>
        </Group>
        <div className="list-rows">
          {EVENTS.map((e) => (
            <Group key={e.minute} wrap="nowrap" mih={52} gap="sm">
              <Text className="score" fz={20} w={40}>
                {e.minute}′
              </Text>
              <Text fw={600} flex={1} truncate>
                {e.player}
              </Text>
              <Text size="xs" c="dimmed">
                {t(`event.type.${e.type}`)}
              </Text>
            </Group>
          ))}
        </div>
      </Stack>
    </Paper>
  )
}

function Feature({ id, title, text, children }: { id: string; title: string; text: string; children: ReactNode }) {
  return (
    <section className="feature" aria-labelledby={id}>
      <Stack gap="md">
        <Title id={id} order={2} className="feature-title">
          {title}
        </Title>
        <Text size="lg" c="dimmed" maw="28em">
          {text}
        </Text>
      </Stack>
      <Paper p="lg" aria-hidden>
        {children}
      </Paper>
    </section>
  )
}

/** What a logged-out visitor sees at `/`: what the app does, and the way in. */
export default function LandingPage() {
  const { t } = useTranslation()
  usePageTitle()
  return (
    <>
      <section className="board bleed">
        <PitchLines kind="field" />
        <div className="bleed-inner hero">
          <Stack gap="lg" align="flex-start">
            <p className="board-eyebrow" style={{ margin: 0 }}>
              {t('landing.eyebrow')}
            </p>
            <Title order={1} className="hero-title">
              {t('landing.title')}
            </Title>
            <Text size="lg" c="dimmed" maw="30em">
              {t('landing.subtitle')}
            </Text>
            <Group gap="sm">
              <Button component={Link} to="/register" size="lg" variant="white" color="dark">
                {t('auth.registerTitle')}
              </Button>
              <Button component={Link} to="/login" size="lg" variant="outline" color="gray.0">
                {t('auth.login')}
              </Button>
            </Group>
          </Stack>
          <HeroPreview />
        </div>
      </section>

      <Feature id="live" title={t('landing.liveTitle')} text={t('landing.liveText')}>
        <Stack gap="sm">
          <Text size="sm" fw={600} c="dimmed">
            {t('event.tapPlayer')}
          </Text>
          <Group grow gap="xs">
            <Button component="span" variant="light" size="lg" px="xs">
              Marko Ilić
            </Button>
            <Button component="span" variant="default" size="lg" px="xs">
              Luka Perović
            </Button>
          </Group>
          <Group grow gap="xs">
            <Button component="span" size="lg" px="xs">
              {t('event.type.GOAL')}
            </Button>
            <Button component="span" variant="default" size="lg" px="xs" leftSection={<CardIcon color="yellow" />}>
              {t('event.type.YELLOW')}
            </Button>
          </Group>
        </Stack>
      </Feature>

      <Feature id="leagues" title={t('landing.leaguesTitle')} text={t('landing.leaguesText')}>
        {TABLE.map(([name, played, points], i) => (
          <div key={name} className="preview-row">
            <Text c="dimmed" fw={600}>
              {i + 1}
            </Text>
            <Text fw={600} truncate>
              {name}
            </Text>
            <Text c="dimmed" ta="right">
              {played}
            </Text>
            <Text className="score" fz={24} ta="right">
              {points}
            </Text>
          </div>
        ))}
      </Feature>

      <Feature id="stats" title={t('landing.statsTitle')} text={t('landing.statsText')}>
        <Stack gap="md">
          <Group gap="sm">
            <Crest name="Marko Ilić" tint="violet" size={48} />
            <Text fw={600} size="lg">
              Marko Ilić
            </Text>
          </Group>
          <dl className="stat-trio">
            {(
              [
                ['goals', 14],
                ['assists', 6],
                ['appearances', 19],
              ] as const
            ).map(([k, n]) => (
              <div key={k}>
                <dt>{t(`profile.${k}`)}</dt>
                <dd className="score stat-trio-value">{n}</dd>
              </div>
            ))}
          </dl>
        </Stack>
      </Feature>

      <section className="board bleed cta-band">
        <div className="bleed-inner">
          <Text component="p" className="score" m={0} fz={36} lh={1.1}>
            {t('landing.title')}
          </Text>
          <Button component={Link} to="/register" size="lg" variant="white" color="dark">
            {t('auth.registerTitle')}
          </Button>
        </div>
      </section>
    </>
  )
}
