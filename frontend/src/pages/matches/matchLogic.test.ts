import { expect, test } from 'vitest'
import { groupByRound, hasStarted, isLive, isoToLocal, localToIso, needsPenalties, pickerOptions, pitchRows, resultFor, roundLabel, shirtNames, showAddGuest, splitMatches, teamsUndecided } from './matchLogic'

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

test('hasStarted: no kickoff counts as started; otherwise once kickoff is reached', () => {
  const now = new Date('2026-11-01T18:00:00Z')
  expect(hasStarted(null, now)).toBe(true)
  expect(hasStarted('2026-11-01T18:00:00Z', now)).toBe(true)
  expect(hasStarted('2026-11-01T18:00:01Z', now)).toBe(false)
})

test('showAddGuest hides the option for blank search and exact (case-insensitive) existing names', () => {
  expect(showAddGuest('  ', ['Bo'])).toBe(false)
  expect(showAddGuest('bo ', ['Bo'])).toBe(false)
  expect(showAddGuest('Bob', ['Bo'])).toBe(true)
})

test('pickerOptions lists the priority players first, then my players, without duplicates, excluding the other side unless selected', () => {
  const opts = pickerOptions({
    first: [{ id: 'r1', name: 'Roster One' }, { id: 'm1', name: 'Mine One' }],
    mine: [{ id: 'm1', name: 'Mine One' }, { id: 'm2', name: 'Mine Two' }, { id: 'x', name: 'Other Side' }],
    excludeIds: ['x', 'r1'],
    value: ['r1'],
  })
  expect(opts).toEqual([
    { value: 'r1', label: 'Roster One' },
    { value: 'm1', label: 'Mine One' },
    { value: 'm2', label: 'Mine Two' },
  ])
})

test('groupByRound keeps API order inside each round and rounds ascending', () => {
  const groups = groupByRound([
    { id: 'a', round: 2 },
    { id: 'b', round: 1 },
    { id: 'c', round: 2 },
    { id: 'd', round: null },
  ])
  expect(groups.map(([round, ms]) => [round, ms.map((m) => m.id)])).toEqual([
    [1, ['b']],
    [2, ['a', 'c']],
    [null, ['d']],
  ])
})

test('roundLabel names the last three rounds and numbers the rest', () => {
  expect(roundLabel(4, 4)).toEqual(['bracket.final', {}])
  expect(roundLabel(3, 4)).toEqual(['bracket.semifinal', {}])
  expect(roundLabel(2, 4)).toEqual(['bracket.quarterfinal', {}])
  expect(roundLabel(1, 4)).toEqual(['bracket.round', { round: 1 }])
  expect(roundLabel(1, 1)).toEqual(['bracket.final', {}])
})

test('needsPenalties only for a level knockout match', () => {
  expect(needsPenalties({ knockout: true, score: { home: 1, away: 1 } })).toBe(true)
  expect(needsPenalties({ knockout: true, score: { home: 2, away: 1 } })).toBe(false)
  expect(needsPenalties({ knockout: false, score: { home: 0, away: 0 } })).toBe(false)
})

test('teamsUndecided: a knockout match waiting for an earlier round', () => {
  const side = (teamId: string | null) => ({ teamId })
  expect(teamsUndecided({ knockout: true, home: side('a'), away: side(null) })).toBe(true)
  expect(teamsUndecided({ knockout: true, home: side('a'), away: side('b') })).toBe(false)
  expect(teamsUndecided({ knockout: false, home: side(null), away: side(null) })).toBe(false) // a pickup has labels, no teams
})

test('isLive: scheduled and past a real kickoff; no date is never live', () => {
  const now = new Date('2026-11-01T20:00:00Z')
  expect(isLive({ status: 'SCHEDULED', kickoffAt: '2026-11-01T19:30:00Z' }, now)).toBe(true)
  expect(isLive({ status: 'SCHEDULED', kickoffAt: '2026-11-01T21:00:00Z' }, now)).toBe(false)
  expect(isLive({ status: 'SCHEDULED', kickoffAt: null }, now)).toBe(false)
  expect(isLive({ status: 'FINISHED', kickoffAt: '2026-11-01T19:30:00Z' }, now)).toBe(false)
})

test('pitchRows: at most three across, biggest rows first, nobody lost', () => {
  const sizes = (n: number) => pitchRows(Array.from({ length: n }, (_, i) => i)).map((r) => r.length)
  expect(sizes(0)).toEqual([])
  expect(sizes(2)).toEqual([2])
  expect(sizes(4)).toEqual([2, 2])
  expect(sizes(6)).toEqual([3, 3])
  expect(sizes(7)).toEqual([3, 2, 2])
  expect(sizes(10)).toEqual([3, 3, 2, 2])
  expect(pitchRows([1, 2, 3, 4]).flat()).toEqual([1, 2, 3, 4])
})

test('shirtNames: surname, an initial for a shared one, the whole name for a stray initial', () => {
  expect(shirtNames(['Marko Ilić', 'Luka Perović'])).toEqual(['Ilić', 'Perović'])
  expect(shirtNames(['Marko Ilić', 'Đorđe Ilić'])).toEqual(['M. Ilić', 'Đ. Ilić'])
  expect(shirtNames(['Marko P', 'Ana'])).toEqual(['Marko P', 'Ana'])
})

test("resultFor reads the score from the player's side", () => {
  expect(resultFor({ home: 2, away: 1 }, 'HOME')).toBe('won')
  expect(resultFor({ home: 2, away: 1 }, 'AWAY')).toBe('lost')
  expect(resultFor({ home: 1, away: 1 }, 'AWAY')).toBe('drawn')
})
