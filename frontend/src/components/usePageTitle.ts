import { useDocumentTitle } from '@mantine/hooks'

/** Names the page in the tab, for screen readers and search; empty until its data has loaded. */
export const usePageTitle = (title?: string | null) => useDocumentTitle(title ? `${title} | MatchFace` : 'MatchFace')
