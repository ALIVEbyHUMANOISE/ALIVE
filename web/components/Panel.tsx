import PixelSprite from './PixelSprite'
import Reveal from './Reveal'
import { badgeFace, type Tone } from '@/lib/hues'
import type { SpriteName } from '@/lib/sprites'

/**
 * One beat of the story.
 *
 * Panels are separated by a full-bleed ink rule rather than by fade gradients or
 * sticky theatre: the app draws its sections the same way, and a page that reads
 * as a stack of cards is the whole brand. The beat's accent arrives here once, so
 * its badge and its cards can never disagree about what colour this part of the
 * page is.
 */
export default function Panel({
  id,
  badge,
  tone,
  title,
  lead,
  body,
  face,
  faceLabel,
  children
}: {
  id: string
  badge: string
  tone: Tone
  title: string | string[]
  lead?: string
  body?: string
  face?: SpriteName
  faceLabel?: string
  children?: React.ReactNode
}) {
  const lines = Array.isArray(title) ? title : [title]

  return (
    <section id={id} className="border-t-[3px] border-ink px-5 py-16 md:px-12 md:py-24">
      <div className="mx-auto w-full max-w-5xl">
        <Reveal>
          <span className={`badge ${badgeFace(tone)}`}>{badge}</span>
        </Reveal>

        <div className="mt-6 flex items-start justify-between gap-6">
          <div className="min-w-0">
            <Reveal delay={60}>
              <h2 className="font-display text-pixel">
                {lines.map((line) => (
                  <span key={line} className="block">
                    {line}
                  </span>
                ))}
              </h2>
            </Reveal>

            {lead && (
              <Reveal delay={120}>
                <p className="mt-6 max-w-2xl font-head text-xl leading-snug md:text-2xl">{lead}</p>
              </Reveal>
            )}
          </div>

          {/* The character is on every screen of the app, so he is on every beat
              here too - including the wide ones, where there is most room for him. */}
          {face && (
            <Reveal delay={180} className="shrink-0">
              <PixelSprite
                name={face}
                size={112}
                label={faceLabel}
                className="max-md:h-20 max-md:w-20"
              />
            </Reveal>
          )}
        </div>

        {body && (
          <Reveal delay={160}>
            <p className="mt-6 max-w-2xl text-lg leading-relaxed text-ink-secondary">{body}</p>
          </Reveal>
        )}

        {children && <div className="mt-10">{children}</div>}
      </div>
    </section>
  )
}
