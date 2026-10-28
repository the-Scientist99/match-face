import {
  Badge, Button, Card, createTheme, Menu, Modal, MultiSelect, NumberInput, Paper, PasswordInput, SegmentedControl, Select, Tabs, TextInput,
} from '@mantine/core'

/** Soft teal-tinted elevation: cards float on the mint page instead of sitting in bordered boxes. */
const softShadow = '0 4px 16px rgba(12, 166, 120, 0.06), 0 1px 3px rgba(0, 0, 0, 0.05)'

/** Direction "Fresh and friendly": mint page, elevated white cards, soft tinted chips, teal gradient accents. */
export const theme = createTheme({
  primaryColor: 'teal',
  primaryShade: { light: 7, dark: 5 },
  defaultRadius: 'md',
  cursorType: 'pointer',
  respectReducedMotion: true,
  fontFamily: 'system-ui, -apple-system, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif',
  shadows: {
    xs: '0 1px 3px rgba(0, 0, 0, 0.05)',
    sm: softShadow,
    md: '0 8px 24px rgba(12, 166, 120, 0.10), 0 2px 6px rgba(0, 0, 0, 0.06)',
  },
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
    Card: Card.extend({ defaultProps: { radius: 24, shadow: 'sm', withBorder: false, padding: 'lg' } }),
    Paper: Paper.extend({ defaultProps: { radius: 24, shadow: 'sm', withBorder: false } }),
    Badge: Badge.extend({ defaultProps: { radius: 'xl', variant: 'light' } }),
    SegmentedControl: SegmentedControl.extend({ defaultProps: { radius: 'xl' } }),
    Tabs: Tabs.extend({ defaultProps: { variant: 'pills', radius: 'xl' } }),
    // soft filled fields (no hard outline), rounded like the cards' inner elements
    TextInput: TextInput.extend({ defaultProps: { variant: 'filled', radius: 'lg' } }),
    PasswordInput: PasswordInput.extend({ defaultProps: { variant: 'filled', radius: 'lg' } }),
    NumberInput: NumberInput.extend({ defaultProps: { variant: 'filled', radius: 'lg' } }),
    Select: Select.extend({ defaultProps: { variant: 'filled', radius: 'lg' } }),
    MultiSelect: MultiSelect.extend({ defaultProps: { variant: 'filled', radius: 'lg' } }),
    // anchored to their trigger and quick to appear: a short scale from the source, not a slide from nowhere
    Menu: Menu.extend({ defaultProps: { radius: 'lg', shadow: 'md', transitionProps: { transition: 'pop-top-right', duration: 150 } } }),
    Modal: Modal.extend({
      defaultProps: { radius: 24, centered: true, overlayProps: { blur: 3, backgroundOpacity: 0.35 }, transitionProps: { transition: 'pop', duration: 180 } },
    }),
  },
})
