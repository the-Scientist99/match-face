import { Badge, Button, Card, createTheme, Menu, Modal, Paper, SegmentedControl } from '@mantine/core'

/** One look for the whole app: system font, tight display tracking, gentle motion, and light/dark aware colours. */
export const theme = createTheme({
  primaryColor: 'teal',
  primaryShade: { light: 7, dark: 5 },
  defaultRadius: 'md',
  cursorType: 'pointer',
  respectReducedMotion: true,
  fontFamily: 'system-ui, -apple-system, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif',
  headings: {
    fontWeight: '700',
    sizes: {
      h1: { fontSize: '2rem', lineHeight: '1.1' },
      h2: { fontSize: '1.625rem', lineHeight: '1.15' },
      h3: { fontSize: '1.375rem', lineHeight: '1.2' },
      h4: { fontSize: '1.125rem', lineHeight: '1.25' },
      h5: { fontSize: '1rem', lineHeight: '1.3' },
    },
  },
  components: {
    Button: Button.extend({ defaultProps: { radius: 'xl' } }),
    Card: Card.extend({ defaultProps: { radius: 'lg', withBorder: true } }),
    Paper: Paper.extend({ defaultProps: { radius: 'lg' } }),
    Badge: Badge.extend({ defaultProps: { radius: 'sm', variant: 'light' } }),
    SegmentedControl: SegmentedControl.extend({ defaultProps: { radius: 'xl' } }),
    // anchored to their trigger and quick to appear: a short scale from the source, not a slide from nowhere
    Menu: Menu.extend({ defaultProps: { radius: 'lg', shadow: 'md', transitionProps: { transition: 'pop-top-right', duration: 150 } } }),
    Modal: Modal.extend({
      defaultProps: { radius: 'lg', centered: true, overlayProps: { blur: 3, backgroundOpacity: 0.35 }, transitionProps: { transition: 'pop', duration: 180 } },
    }),
  },
})
