import { afterEach, expect, test } from 'vitest'
import { htmlLang, pickInitialLang, readStoredLang, storeLang } from './lang'

const original = Object.getOwnPropertyDescriptor(globalThis, 'localStorage')
afterEach(() => {
  if (original) Object.defineProperty(globalThis, 'localStorage', original)
  else delete (globalThis as { localStorage?: unknown }).localStorage
})

function blockStorage() {
  Object.defineProperty(globalThis, 'localStorage', {
    configurable: true,
    get() {
      throw new DOMException('The operation is insecure.', 'SecurityError')
    },
  })
}

test('stored choice wins, otherwise English browsers get en and everyone else me', () => {
  expect(pickInitialLang('me', 'en-US')).toBe('me')
  expect(pickInitialLang('en', 'sr-Latn-ME')).toBe('en')
  expect(pickInitialLang(null, 'en-GB')).toBe('en')
  expect(pickInitialLang(null, 'EN')).toBe('en')
  expect(pickInitialLang(null, 'sr-Latn-ME')).toBe('me')
  expect(pickInitialLang('de', 'de-DE')).toBe('me')
})

test('blocked site storage reads as no stored choice instead of throwing', () => {
  blockStorage()
  expect(readStoredLang()).toBeNull()
})

test('saving the choice with blocked site storage does not throw', () => {
  blockStorage()
  expect(() => storeLang('en')).not.toThrow()
})

test('html lang attribute uses a valid tag for Montenegrin', () => {
  expect(htmlLang('me')).toBe('sr-Latn-ME')
  expect(htmlLang('en')).toBe('en')
})
