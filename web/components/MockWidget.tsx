import PixelSprite from './PixelSprite'
import type { SpriteName } from '@/lib/sprites'

/**
 * The home-screen widget, drawn in HTML.
 *
 * Not a screenshot and not a mockup with invented numbers: this is the same
 * composition the app puts on a launcher - the hourglass and the day, the figure
 * in the pixel face, the allowance and the total on one line, the meter in the
 * ring's own colours, then the three apps it is made of.
 */

/** That day's shares of the bar, in the order the app hands out ring colours. */
const SEGMENTS = [
  { colour: 'var(--alive-ink)', share: 45 },
  { colour: 'var(--alive-gold_reward_mid)', share: 35 },
  { colour: 'var(--alive-blue_work_mid)', share: 20 }
]

export default function MockWidget({
  label,
  figure,
  allowance,
  screen,
  apps,
  face,
  faceLabel
}: {
  label: string
  figure: string
  allowance: string
  screen: string
  apps: { name: string; minutes: string }[]
  face: SpriteName
  faceLabel?: string
}) {
  return (
    <div className="card p-5">
      <div className="flex items-center gap-2">
        <PixelSprite name="hourglass" size={18} />
        <span className="text-sm text-ink-secondary">{label}</span>
        <PixelSprite name={face} size={44} label={faceLabel} className="ml-auto" />
      </div>

      <div className="mt-2 font-display text-2xl leading-none md:text-3xl">{figure}</div>

      <div className="mt-2 flex items-baseline justify-between gap-3 text-sm text-ink-secondary">
        <span>{allowance}</span>
        <span>{screen}</span>
      </div>

      <div className="mt-3 flex h-4 overflow-hidden rounded-step border-[3px] border-ink bg-white">
        {SEGMENTS.map((segment) => (
          <span
            key={segment.share}
            className="h-full border-r-2 border-ink last:border-r-0"
            style={{ width: `${segment.share}%`, background: segment.colour }}
          />
        ))}
      </div>

      <ul className="mt-4 space-y-2">
        {apps.map((app, index) => (
          <li key={app.name} className="flex items-center gap-2">
            <span
              className="h-3 w-3 shrink-0 border-2 border-ink"
              style={{ background: SEGMENTS[index % SEGMENTS.length].colour }}
            />
            <span className="min-w-0 flex-1 truncate font-medium">{app.name}</span>
            <span className="text-ink-secondary">{app.minutes}</span>
          </li>
        ))}
      </ul>
    </div>
  )
}
