import { Button, Text } from '@mantine/core'
import { useTranslation } from 'react-i18next'
import { Navigate } from 'react-router'
import { errorCode } from '../api/errors'
import { useAuth } from './AuthProvider'
import { useLoginPath } from './RequireAuth'

/** A page that failed to load. A visitor's not found may be private, so they log in rather than dead-end. */
export default function LoadError({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  const { t } = useTranslation()
  const { user } = useAuth()
  const loginPath = useLoginPath()
  const code = errorCode(error)
  if (!user && code === 'NOT_FOUND') return <Navigate to={loginPath} replace />
  return (
    <div className="empty" role="alert">
      <Text fw={500} c="var(--mantine-color-error)">
        {t(`errors.${code}`, { defaultValue: t('errors.UNKNOWN') })}
      </Text>
      {onRetry && (
        <Button variant="default" onClick={onRetry}>
          {t('common.retry')}
        </Button>
      )}
    </div>
  )
}
