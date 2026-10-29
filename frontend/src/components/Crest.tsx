import type { CSSProperties } from 'react'

const TINTS = ['blue', 'orange', 'violet', 'green', 'pink', 'yellow'] as const
export type Tint = (typeof TINTS)[number] | 'gray'

/** A stable colour per team name, so a team looks the same on every screen; no name (TBD) is gray. */
export function tintOf(name: string | null | undefined): Tint {
  if (!name) return 'gray'
  let h = 0
  for (const ch of name) h = (h * 31 + ch.codePointAt(0)!) | 0
  return TINTS[Math.abs(h) % TINTS.length]
}

/** Both sides' tints; when the names land on the same colour, the away side takes the next one. */
export function sideTints(home: string | null, away: string | null): Record<'HOME' | 'AWAY', Tint> {
  const h = tintOf(home)
  const a = tintOf(away)
  return { HOME: h, AWAY: a === h && a !== 'gray' ? TINTS[(TINTS.indexOf(a) + 1) % TINTS.length] : a }
}

/** "Blue Harbour" → "BH", "Non-bibs" → "NB", "Bibs" → "BI" */
export function initials(name: string): string {
  const words = name.split(/[\s-]+/).filter(Boolean)
  const letters = words.length > 1 ? [Array.from(words[0])[0], Array.from(words[1])[0]] : Array.from(words[0] ?? '?').slice(0, 2)
  return letters.join('').toLocaleUpperCase()
}

/**
 * A team's logo or player's photo (`src`), else their initials in a disc of their colour. Decorative: the
 * name is always written next to it.
 */
export default function Crest({ name, tint, size = 28, src }: { name: string | null; tint?: Tint; size?: number; src?: string | null }) {
  const style = { '--crest-size': `${size}px` } as CSSProperties
  if (src) return <img className="crest" data-tint={tint ?? tintOf(name)} style={style} src={src} alt="" loading="lazy" />
  return (
    <span className="crest" data-tint={tint ?? tintOf(name)} style={style} aria-hidden>
      {name ? initials(name) : '?'}
    </span>
  )
}
