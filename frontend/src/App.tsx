import { Center, Loader } from '@mantine/core'
import { createBrowserRouter } from 'react-router'
import { useAuth } from './auth/AuthProvider'
import RequireAuth from './auth/RequireAuth'
import Layout from './components/Layout'
import LoginPage from './pages/auth/LoginPage'
import RegisterPage from './pages/auth/RegisterPage'
import ClaimPage from './pages/claim/ClaimPage'
import CompetitionPage from './pages/competitions/CompetitionPage'
import NewCompetitionPage from './pages/competitions/NewCompetitionPage'
import HomePage from './pages/home/HomePage'
import LandingPage from './pages/landing/LandingPage'
import MatchPage from './pages/matches/MatchPage'
import NewMatchPage from './pages/matches/NewMatchPage'
import PlayerProfilePage from './pages/players/PlayerProfilePage'
import PlayersPage from './pages/players/PlayersPage'
import TeamPage from './pages/teams/TeamPage'
import TeamsPage from './pages/teams/TeamsPage'

/** `/` is the landing page for visitors and My matches once logged in, so a link to the site never lands on a login form. */
function Index() {
  const { user, ready } = useAuth()
  if (!ready) {
    return (
      <Center mt="xl">
        <Loader />
      </Center>
    )
  }
  return user ? <HomePage /> : <LandingPage />
}

export const router = createBrowserRouter([
  {
    path: '/',
    element: <Layout />,
    children: [
      { index: true, element: <Index /> },
      { path: 'login', element: <LoginPage /> },
      { path: 'register', element: <RegisterPage /> },
      { path: 'claim/:token', element: <ClaimPage /> },
      {
        element: <RequireAuth />,
        children: [
          { path: 'players', element: <PlayersPage /> },
          { path: 'players/:id', element: <PlayerProfilePage /> },
          { path: 'teams', element: <TeamsPage /> },
          { path: 'teams/:id', element: <TeamPage /> },
          { path: 'matches/new', element: <NewMatchPage /> },
          { path: 'competitions/new', element: <NewCompetitionPage /> },
          { path: 'competitions/:id', element: <CompetitionPage /> },
          { path: 'matches/:id', element: <MatchPage /> },
        ],
      },
    ],
  },
])
