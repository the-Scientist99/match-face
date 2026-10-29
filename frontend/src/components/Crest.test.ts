import { expect, test } from 'vitest'
import { initials, sideTints, tintOf } from './Crest'

test('initials', () => {
  expect(initials('Blue Harbour')).toBe('BH')
  expect(initials('Old Town FC')).toBe('OT')
  expect(initials('Non-bibs')).toBe('NB')
  expect(initials('Bibs')).toBe('BI')
  expect(initials('đorđe lazić')).toBe('ĐL')
})

test('a name always gets the same tint; no name is gray', () => {
  expect(tintOf('Blue Harbour')).toBe(tintOf('Blue Harbour'))
  expect(tintOf(null)).toBe('gray')
})

test('the two sides never share a tint', () => {
  const t = sideTints('Same', 'Same')
  expect(t.HOME).not.toBe(t.AWAY)
  expect(sideTints(null, null)).toEqual({ HOME: 'gray', AWAY: 'gray' })
})
