/** The ball's edge patches (the "Kickoff" logo); they stop at a white rim, so the ball stays round on any background. */
const PATCHES =
  'M50.0 25.0 L35.7 14.6 L41.2 -2.1 L58.8 -2.1 L64.3 14.6Z M73.8 42.3 L79.2 25.5 L96.9 25.5 L102.3 42.3 L88.0 52.6Z ' +
  'M64.7 70.2 L82.3 70.2 L87.8 87.0 L73.5 97.4 L59.2 87.0Z M35.3 70.2 L40.8 87.0 L26.5 97.4 L12.2 87.0 L17.7 70.2Z ' +
  'M26.2 42.3 L12.0 52.6 L-2.3 42.3 L3.1 25.5 L20.8 25.5Z'

/** MatchFace's mark: a football whose centre patch is a smiling face. Decorative; the name is always written beside it. */
export function KickoffMark({ size = 28 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 100 100" aria-hidden>
      {/* every copy draws the same circle, so a repeated id is harmless */}
      <defs>
        <clipPath id="kickoff-rim">
          <circle cx="50" cy="50" r="39" />
        </clipPath>
      </defs>
      <circle cx="50" cy="50" r="44" fill="#ffffff" />
      <path clipPath="url(#kickoff-rim)" fill="#0b1f19" d={PATCHES} />
      <circle cx="50" cy="50" r="44" fill="none" stroke="#0b1f19" strokeWidth="5" />
      <circle cx="41" cy="45" r="4.5" fill="#0b1f19" />
      <circle cx="59" cy="45" r="4.5" fill="#0b1f19" />
      <path d="M37 56 Q50 69 63 56" fill="none" stroke="var(--mantine-color-teal-text)" strokeWidth="5.5" strokeLinecap="round" />
    </svg>
  )
}

/** The mark beside "Match" + "Face" in two tones. The name is a brand, so it isn't translated. */
export default function Logo({ size = 30 }: { size?: number }) {
  return (
    <>
      <KickoffMark size={size} />
      <span className="wordmark">
        Match<span>Face</span>
      </span>
    </>
  )
}
