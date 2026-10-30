import { Text } from '@mantine/core'
import type { ReactNode } from 'react'

/** Nothing here yet: says so in a panel, with the next step when there is one. */
export default function Empty({ text, action }: { text: string; action?: ReactNode }) {
  return (
    <div className="empty">
      <Text c="dimmed">{text}</Text>
      {action}
    </div>
  )
}
