import { useQueryClient } from '@tanstack/react-query'
import { createContext, type ReactNode, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { api, type AuthResponse, type Me, onSessionExpired, refreshAccessToken, setAccessToken } from '../api/client'
import type { components } from '../api/schema'
import type { Lang } from '../lang'

type RegisterRequest = components['schemas']['RegisterRequest']

type AuthState = {
  user: Me | null
  ready: boolean
  login(email: string, password: string): Promise<void>
  register(req: RegisterRequest): Promise<void>
  logout(): Promise<void>
  setLocale(l: Lang): Promise<void>
}

const AuthContext = createContext<AuthState | null>(null)

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth outside AuthProvider')
  return ctx
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const { i18n } = useTranslation()
  const queryClient = useQueryClient()
  const [user, setUser] = useState<Me | null>(null)
  const [ready, setReady] = useState(false)

  const start = useCallback(
    (res: AuthResponse) => {
      setAccessToken(res.accessToken)
      setUser(res.user)
      void i18n.changeLanguage(res.user.locale)
    },
    [i18n],
  )

  useEffect(() => {
    void refreshAccessToken().then((res) => {
      if (res && res !== 'offline') start(res)
      setReady(true)
    })
    return onSessionExpired(() => {
      setUser(null)
      queryClient.clear()
    })
  }, [start, queryClient])

  const value = useMemo<AuthState>(
    () => ({
      user,
      ready,
      async login(email, password) {
        const { data, error } = await api.POST('/api/auth/login', { body: { email, password } })
        if (error || !data) throw error
        start(data as AuthResponse)
      },
      async register(req) {
        const { data, error } = await api.POST('/api/auth/register', { body: req })
        if (error || !data) throw error
        start(data as AuthResponse)
      },
      async logout() {
        await api.POST('/api/auth/logout').catch(() => undefined)
        setAccessToken(null)
        setUser(null)
        queryClient.clear()
      },
      async setLocale(l) {
        await i18n.changeLanguage(l)
        if (!user) return
        const { data, error } = await api.PATCH('/api/me', { body: { locale: l } })
        if (error || !data) throw error
        setUser(data as Me)
      },
    }),
    [user, ready, start, i18n, queryClient],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
