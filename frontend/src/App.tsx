import { createBrowserRouter } from 'react-router'
import RequireAuth from './auth/RequireAuth'
import Layout from './components/Layout'
import LoginPage from './pages/auth/LoginPage'
import RegisterPage from './pages/auth/RegisterPage'
import ClaimPage from './pages/claim/ClaimPage'
import CompetitionPage from './pages/competitions/CompetitionPage'
import NewCompetitionPage from './pages/competitions/NewCompetitionPage'
import HomePage from './pages/home/HomePage'
import MatchPage from './pages/matches/MatchPage'
import NewMatchPage from './pages/matches/NewMatchPage'
import PlayerProfilePage from './pages/players/PlayerProfilePage'
import PlayersPage from './pages/players/PlayersPage'
import TeamPage from './pages/teams/TeamPage'
import TeamsPage from './pages/teams/TeamsPage'

export const router = createBrowserRouter([
  {
    path: '/',
    element: <Layout />,
    children: [
      { path: 'login', element: <LoginPage /> },
      { path: 'register', element: <RegisterPage /> },
      { path: 'claim/:token', element: <ClaimPage /> },
      {
        element: <RequireAuth />,
        children: [
          { index: true, element: <HomePage /> },
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
