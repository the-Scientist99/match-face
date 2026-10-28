import { expect, test } from 'vitest'
import { isoToLocal, localToIso, showAddGuest, splitMatches } from './matchLogic'

const m = (id: string, status: 'SCHEDULED' | 'FINISHED' | 'CANCELLED', kickoffAt: string | null) =>
  ({ id, status, kickoffAt }) as Parameters<typeof splitMatches>[0][number]

test('splitMatches: scheduled are upcoming soonest first (no kickoff last); finished and cancelled are recent in API order', () => {
  const { upcoming, recent } = splitMatches([
    m('none', 'SCHEDULED', null),
    m('late', 'SCHEDULED', '2026-12-01T10:00:00Z'),
    m('done', 'FINISHED', '2026-10-01T10:00:00Z'),
    m('soon', 'SCHEDULED', '2026-11-01T10:00:00Z'),
    m('off', 'CANCELLED', '2026-09-01T10:00:00Z'),
  ])
  expect(upcoming.map((x) => x.id)).toEqual(['soon', 'late', 'none'])
  expect(recent.map((x) => x.id)).toEqual(['done', 'off'])
})

test('datetime-local values round-trip through ISO, and empty means no kickoff', () => {
  expect(localToIso('')).toBeUndefined()
  const iso = localToIso('2026-11-01T18:30')
  expect(iso).toMatch(/^2026-11-0[12]T\d\d:30:00\.000Z$/)
  expect(isoToLocal(iso!)).toBe('2026-11-01T18:30')
  expect(isoToLocal(null)).toBe('')
})

test('showAddGuest hides the option for blank search and exact (case-insensitive) existing names', () => {
  expect(showAddGuest('  ', ['Bo'])).toBe(false)
  expect(showAddGuest('bo ', ['Bo'])).toBe(false)
  expect(showAddGuest('Bob', ['Bo'])).toBe(true)
})
