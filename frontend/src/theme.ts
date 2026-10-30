import {
  Badge, Button, Card, Checkbox, createTheme, Fieldset, Menu, Modal, MultiSelect, NumberInput, Pagination, Paper, PasswordInput, SegmentedControl, Select, Tabs, TextInput,
} from '@mantine/core'

const DISPLAY = '"Barlow Semi Condensed", system-ui, sans-serif'
const field = { size: 'md' } as const

/** "Matchday Board": hairlines instead of shadows, type-led lists, solid teal accents and one dark scoreboard per screen. */
export const theme = createTheme({
  primaryColor: 'teal',
  // filled buttons: teal 9 keeps white text at 5:1 in both schemes;
  // teal text uses --mantine-color-teal-text, which dark mode lightens on its own
  primaryShade: { light: 9, dark: 9 },
  autoContrast: true,
  defaultRadius: 'md',
  cursorType: 'pointer',
  respectReducedMotion: true,
  fontFamily: '"Instrument Sans", system-ui, -apple-system, "Segoe UI", Roboto, Arial, sans-serif',
  // nothing on screen goes below 14px
  fontSizes: { xs: '0.875rem', sm: '0.9375rem', md: '1rem', lg: '1.1875rem', xl: '1.5rem' },
  radius: { xs: '4px', sm: '6px', md: '10px', lg: '16px', xl: '16px' },
  shadows: {
    xs: 'none',
    sm: 'none',
    md: '0 12px 32px rgba(6, 26, 20, 0.18)', // floating layers only: menus, dialogs
  },
  headings: {
    fontFamily: DISPLAY,
    fontWeight: '700',
    sizes: {
      h1: { fontSize: 'clamp(2.25rem, 6vw, 3rem)', lineHeight: '1.05' },
      h2: { fontSize: '1.5rem', lineHeight: '1.2' },
      h3: { fontSize: '1.25rem', lineHeight: '1.2' },
      h4: { fontSize: '1.125rem', lineHeight: '1.25' },
      h5: { fontSize: '1rem', lineHeight: '1.3' },
    },
  },
  components: {
    Button: Button.extend({ defaultProps: { size: 'md' } }),
    Card: Card.extend({ defaultProps: { radius: 'lg', shadow: 'none', withBorder: true, padding: 'lg' } }),
    Paper: Paper.extend({ defaultProps: { radius: 'lg', shadow: 'none', withBorder: true } }),
    Fieldset: Fieldset.extend({ defaultProps: { radius: 'lg' } }),
    Badge: Badge.extend({ defaultProps: { radius: 'sm', variant: 'light' } }),
    SegmentedControl: SegmentedControl.extend({ defaultProps: { radius: 'md', size: 'md' } }),
    Tabs: Tabs.extend({ defaultProps: { variant: 'default' } }),
    Pagination: Pagination.extend({ defaultProps: { size: 'lg' } }),
    TextInput: TextInput.extend({ defaultProps: field }),
    PasswordInput: PasswordInput.extend({ defaultProps: field }),
    NumberInput: NumberInput.extend({ defaultProps: { ...field, hideControls: true } }), // typed, not nudged: the steppers are 20px and unnamed
    Checkbox: Checkbox.extend({ defaultProps: { size: 'md' } }),
    Select: Select.extend({ defaultProps: field }),
    MultiSelect: MultiSelect.extend({ defaultProps: field }),
    // anchored to their trigger and quick to appear: a short scale from the source, not a slide from nowhere
    Menu: Menu.extend({ defaultProps: { radius: 'lg', shadow: 'md', transitionProps: { transition: 'pop-top-right', duration: 150 } } }),
    Modal: Modal.extend({
      defaultProps: { radius: 'lg', shadow: 'md', centered: true, overlayProps: { backgroundOpacity: 0.55 }, transitionProps: { transition: 'pop', duration: 180 } },
    }),
  },
})
