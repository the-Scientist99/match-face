import { createBrowserRouter } from 'react-router'
import Layout from './components/Layout'
import HomePage from './pages/home/HomePage'

export const router = createBrowserRouter([
  { path: '/', element: <Layout />, children: [{ index: true, element: <HomePage /> }] },
])
