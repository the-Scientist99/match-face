import { createBrowserRouter } from 'react-router'
import RequireAuth from './auth/RequireAuth'
import Layout from './components/Layout'
import LoginPage from './pages/auth/LoginPage'
import RegisterPage from './pages/auth/RegisterPage'
import HomePage from './pages/home/HomePage'
import MatchPage from './pages/matches/MatchPage'
import NewMatchPage from './pages/matches/NewMatchPage'
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
      {
        element: <RequireAuth />,
        children: [
          { index: true, element: <HomePage /> },
          { path: 'players', element: <PlayersPage /> },
          { path: 'teams', element: <TeamsPage /> },
          { path: 'teams/:id', element: <TeamPage /> },
          { path: 'matches/new', element: <NewMatchPage /> },
          { path: 'matches/:id', element: <MatchPage /> },
        ],
      },
    ],
  },
])
