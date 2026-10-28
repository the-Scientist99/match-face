import { expect, test } from 'vitest'
import en from './en.json'
import me from './me.json'

const flat = (o: object, p = ''): Record<string, unknown> =>
  Object.entries(o).reduce(
    (acc, [k, v]) => ({ ...acc, ...(v && typeof v === 'object' ? flat(v, `${p}${k}.`) : { [`${p}${k}`]: v }) }),
    {},
  )

test('me and en have the same keys', () => {
  expect(Object.keys(flat(me)).sort()).toEqual(Object.keys(flat(en)).sort())
})

test('no empty strings', () => {
  for (const m of [flat(me), flat(en)]) for (const v of Object.values(m)) expect(v).not.toBe('')
})
