import { Anchor, Button, Paper, PasswordInput, Stack, Text, TextInput, Title } from '@mantine/core'
import { type FormEvent, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router'
import { fieldErrors, useNotifyError } from '../../api/errors'
import { useAuth } from '../../auth/AuthProvider'
import { safeNext } from '../../auth/RequireAuth'
import { usePageTitle } from '../../components/usePageTitle'

export default function RegisterPage() {
  const { t, i18n } = useTranslation()
  usePageTitle(t('auth.registerTitle'))
  const { user, register } = useAuth()
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const notifyError = useNotifyError()
  const [displayName, setDisplayName] = useState('')
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
      await register({ displayName, email, password, locale: i18n.language })
      navigate(next, { replace: true })
    } catch (err) {
      setErrors(fieldErrors(err))
      notifyError(err)
    } finally {
      setBusy(false)
    }
  }

  return (
    <Paper p="xl" maw={420} mx="auto" mt={{ base: "md", sm: 56 }}>
      <Stack component="form" onSubmit={submit}>
        <Title order={1}>{t('auth.registerTitle')}</Title>
        <TextInput
          label={t('auth.displayName')}
          autoComplete="name"
          required
          maxLength={50}
          value={displayName}
          onChange={(e) => setDisplayName(e.currentTarget.value)}
          error={errors.displayName && t(errors.displayName)}
        />
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
          description={t('auth.passwordHint')}
          autoComplete="new-password"
          required
          minLength={8}
          value={password}
          onChange={(e) => setPassword(e.currentTarget.value)}
          error={errors.password && t(errors.password)}
        />
        <Button type="submit" size="lg" loading={busy}>
          {t('auth.register')}
        </Button>
        <Text size="sm">
          {t('auth.haveAccount')}{' '}
          <Anchor component={Link} to={`/login?next=${encodeURIComponent(next)}`}>
            {t('auth.login')}
          </Anchor>
        </Text>
      </Stack>
    </Paper>
  )
}
