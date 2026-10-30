import { Skeleton, Stack } from '@mantine/core'
import { useTranslation } from 'react-i18next'

/** The shape of what's coming, so the page doesn't jump when it arrives. */
export default function Loading({ rows = 3 }: { rows?: number }) {
  const { t } = useTranslation()
  return (
    <Stack gap="sm" role="status" aria-label={t('common.loading')}>
      {Array.from({ length: rows }, (_, i) => (
        <Skeleton key={i} height={64} radius="lg" />
      ))}
    </Stack>
  )
}
