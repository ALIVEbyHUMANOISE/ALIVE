import PixelSprite from './PixelSprite'
import { accentSprites, faces, fills } from '@/lib/hues'
import type { SpriteName } from '@/lib/sprites'

/**
 * The small saturated objects the app's screens are actually made of.
 *
 * Each one is the web version of a function in `ui/AliveComponents.kt`, with the
 * same numbers: a MINOR edge is 2px with a 4px shadow, a tile's radius is 12, a
 * step's is 4, a pip's box is 16. The app adds the shadow's width to its padding
 * because its shadow lives *inside* the view's bounds; here the shadow is a
 * `box-shadow` outside the box, so equal padding is the faithful translation -
 * the content ends up the same distance from the ink on every side either way.
 */

/** 0 - the slice's own colour, with the ink edge the chart draws. `ui.swatch()`. */
export function Swatch({ fill, size = 'h-4 w-4' }: { fill: string; size?: string }) {
  return <span aria-hidden className={`${size} shrink-0 rounded-step border-2 border-ink ${fill}`} />
}

/**
 * 1 - a kind of doing as one object: hue, ink edge, shadow, pixel mark.
 * `ui.iconTile()` is the thing a board is scanned by, and it is why a list of
 * verbs can be pointed at instead of read.
 */
export function HueTile({
  fill,
  mark,
  label,
  size = 48
}: {
  fill: string
  mark: SpriteName
  label?: string
  size?: number
}) {
  // The app gives its glyph 28 of the 48 it is handed, so a tile never reads as
  // a circle with a sticker in it.
  const sprite = Math.round((size * 7) / 12)

  return (
    <span
      className={`grid shrink-0 place-items-center rounded-tile border-2 border-ink shadow-raise-sm ${fill}`}
      style={{ width: size, height: size }}
    >
      <PixelSprite name={mark} size={sprite} label={label} />
    </span>
  )
}

/** 2 - a square of hue with a figure in it: a rank, an initial. `ui.textTile()`. */
export function TextTile({
  value,
  fill,
  size = 32
}: {
  value: string
  fill: string
  size?: number
}) {
  return (
    <span
      className={`grid shrink-0 place-items-center rounded-tile border-2 border-ink font-display text-base text-ink shadow-raise-sm ${fill}`}
      style={{ width: size, height: size }}
    >
      {value}
    </span>
  )
}

/**
 * 3 - the flat pill on a card's top edge, and the card it is tucked behind.
 * `ui.categoryTab()` plus the wrapper in `MainActivity.questCard()`: the tab is
 * laid down first and the card covers its lower half, because the edge belongs
 * to the card and a border on the tab would draw a second box around a flag.
 *
 * A tab holds no words, which is what lets it wear a mid tone - `AlivePaletteTest`
 * refuses text on `*_mid` and has nothing to say about a fill nobody reads.
 */
export function TabCard({
  fill,
  title,
  body,
  mark,
  children
}: {
  fill: string
  title: string
  body: string
  mark?: SpriteName
  children?: React.ReactNode
}) {
  return (
    <div className="relative mt-2">
      <span aria-hidden className={`absolute left-4 top-0 h-4 w-24 rounded-tile ${fill}`} />
      <article className="card relative z-10 h-full p-5">
        <div className="flex items-center gap-3">
          {mark && <PixelSprite name={mark} size={24} />}
          <h3 className="font-head text-lg font-bold">{title}</h3>
        </div>
        <div className="rule mt-3" />
        <p className="mt-3 leading-relaxed text-ink-secondary">{body}</p>
        {children}
      </article>
    </div>
  )
}

/**
 * 4 - one cell of the day's facts. `ui.statTile()`: the name in small caps, the
 * figure in the pixel voice, the unit on the same baseline.
 *
 * The face carries meaning and not mood - gold for something earned, blue for a
 * budget, resting green for a day that held - which is what stops a colourful
 * tile from being a tile nobody learns anything from.
 */
export function StatTile({
  name,
  value,
  unit,
  face = faces.paper,
  mark
}: {
  name: string
  value: string
  unit?: string
  face?: string
  mark?: SpriteName
}) {
  return (
    <div className={`rounded-tile border-2 border-ink p-4 shadow-raise-sm ${face}`}>
      <p className="text-[11px] font-bold uppercase tracking-[0.08em] text-ink-secondary">{name}</p>
      <p className="mt-2 flex items-end gap-2">
        <span className="font-display text-2xl leading-none">{value}</span>
        {mark && <PixelSprite name={mark} size={20} />}
        {unit && <span className="pb-1 text-sm text-ink-secondary">{unit}</span>}
      </p>
    </div>
  )
}

/** 5 - one proportional bar: this row's share of the whole it belongs to. */
export function Meter({ share, fill }: { share: number; fill: string }) {
  const clamped = Math.max(0, Math.min(1, share))

  return (
    <div className="flex h-2 w-full overflow-hidden rounded-step border-2 border-ink bg-white">
      <span style={{ width: `${clamped * 100}%` }} className={`h-full ${fill}`} />
    </div>
  )
}

/**
 * 6 - counted minutes rather than a gauge: whole blocks for what is left, empty
 * blocks for what is spent. `ui.timeBar()`.
 */
export function TimeBar({
  remaining,
  limit,
  segments = 10,
  fill = fills.forest
}: {
  remaining: number
  limit: number
  segments?: number
  fill?: string
}) {
  if (limit <= 0) return null
  const filled = Math.ceil((remaining / limit) * segments)

  return (
    <div className="flex flex-wrap gap-[2px]">
      {Array.from({ length: segments }, (_, index) => (
        <span
          key={index}
          aria-hidden
          className={`h-4 w-4 rounded-step border-2 border-ink ${index < filled ? fill : 'bg-white'}`}
        />
      ))}
    </div>
  )
}

/**
 * 7 - a run of days as squares. `ui.streakPips()`: the shape of the number, never
 * the number itself, so the sentence beside it stays authoritative.
 */
export function Pips({
  filled,
  total = 7,
  fill = fills.gold
}: {
  filled: number
  total?: number
  fill?: string
}) {
  return (
    <div aria-hidden className="flex gap-1">
      {Array.from({ length: total }, (_, index) => (
        <span
          key={index}
          className={`h-4 w-4 rounded-step border-2 border-ink ${index < filled ? fill : 'bg-white'}`}
        />
      ))}
    </div>
  )
}

/** 8 - time that was paid out, on paper the colour of a reward. `ui.rewardChip()`. */
export function RewardChip({ value }: { value: string }) {
  return (
    <span className="rounded-pill border-2 border-ink bg-gold-pale px-3 py-1 text-sm font-bold text-gold-dark shadow-raise-sm">
      {value}
    </span>
  )
}

/**
 * 9 - three pixel shapes, one row, one per beat. `ui.accents()`.
 *
 * The count is fixed and the seed is per beat, so the same corner always shows
 * the same shapes. Scatter that re-randomises per component stops being an
 * accent and becomes noise - the mistake this file is written to avoid.
 */
export function Accents({ seed, size = 16, className = '' }: { seed: number; size?: number; className?: string }) {
  return (
    <div aria-hidden className={`flex gap-2 ${className}`}>
      {[0, 1, 2].map((i) => (
        <PixelSprite
          key={i}
          name={accentSprites[(seed * 7 + i * 5) % accentSprites.length]}
          size={size}
        />
      ))}
    </div>
  )
}
