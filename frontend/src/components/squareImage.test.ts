import { expect, test } from 'vitest'
import { centerSquare } from './squareImage'

test('centerSquare crops the long side evenly', () => {
  expect(centerSquare(600, 400)).toEqual({ sx: 100, sy: 0, side: 400 })
  expect(centerSquare(400, 601)).toEqual({ sx: 0, sy: 100, side: 400 })
  expect(centerSquare(256, 256)).toEqual({ sx: 0, sy: 0, side: 256 })
})
