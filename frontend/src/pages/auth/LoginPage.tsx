import { Anchor, Button, Paper, PasswordInput, Stack, Text, TextInput, Title } from '@mantine/core'
import { type FormEvent, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router'
import { fieldErrors, useNotifyError } from '../../api/errors'
import { useAuth } from '../../auth/AuthProvider'
import { safeNext } from '../../auth/RequireAuth'

export default function LoginPage() {
  const { t } = useTranslation()
  const { user, login } = useAuth()
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const notifyError = useNotifyError()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [busy, setBusy] = useState(false)
  const next = safeNext(params.get('next'))

  if (user) return <Navigate to={next} replace />

  async function submit(e: FormEvent) {
    e.preventDefault()
    setBusy(true)
    try {
      await login(email, password)
      navigate(next, { replace: true })
    } catch (err) {
      setErrors(fieldErrors(err))
      notifyError(err)
    } finally {
      setBusy(false)
    }
  }

  return (
    <Paper withBorder p="xl" maw={420} mx="auto" mt="xl">
      <Stack component="form" onSubmit={submit}>
        <Title order={2}>{t('auth.loginTitle')}</Title>
        <TextInput
          label={t('auth.email')}
          type="email"
          autoComplete="email"
          required
          value={email}
          onChange={(e) => setEmail(e.currentTarget.value)}
          error={errors.email && t(errors.email)}
        />
        <PasswordInput
          label={t('auth.password')}
          autoComplete="current-password"
          required
          value={password}
          onChange={(e) => setPassword(e.currentTarget.value)}
          error={errors.password && t(errors.password)}
        />
        <Button type="submit" loading={busy}>
          {t('auth.login')}
        </Button>
        <Text size="sm">
          {t('auth.noAccount')}{' '}
          <Anchor component={Link} to={`/register?next=${encodeURIComponent(next)}`}>
            {t('auth.register')}
          </Anchor>
        </Text>
      </Stack>
    </Paper>
  )
}
