/** The pitch's markings behind a board: the halfway line and centre circle, and for a whole field its box too. Decorative. */
export default function PitchLines({ kind = 'halfway' }: { kind?: 'halfway' | 'field' }) {
  return (
    <svg className="board-lines" viewBox="0 0 1440 400" preserveAspectRatio="xMidYMid slice" aria-hidden>
      {kind === 'field' ? (
        <>
          <line x1="980" y1="0" x2="980" y2="400" />
          <circle cx="980" cy="200" r="120" />
          <rect x="1240" y="60" width="200" height="280" />
        </>
      ) : (
        <>
          <line x1="720" y1="0" x2="720" y2="400" />
          <circle cx="720" cy="200" r="150" />
        </>
      )}
    </svg>
  )
}
