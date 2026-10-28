import { notifications } from '@mantine/notifications'
import { useTranslation } from 'react-i18next'

type Problem = { code?: unknown; errors?: unknown }

/** The ProblemDetail `code` of an API error, or UNKNOWN. */
export function errorCode(err: unknown): string {
  const code = (err as Problem | null)?.code
  return typeof code === 'string' ? code : 'UNKNOWN'
}

/** Field → i18n key (`validation.<Constraint>`) for a VALIDATION error; empty otherwise. */
export function fieldErrors(err: unknown): Record<string, string> {
  const errors = (err as Problem | null)?.errors
  if (!errors || typeof errors !== 'object') return {}
  return Object.fromEntries(Object.entries(errors).map(([field, c]) => [field, `validation.${String(c)}`]))
}

export function useNotifyError(): (err: unknown) => void {
  const { t } = useTranslation()
  return (err) => {
    const code = errorCode(err)
    notifications.show({ color: 'red', message: t(`errors.${code}`, { defaultValue: t('errors.UNKNOWN') }) })
  }
}
