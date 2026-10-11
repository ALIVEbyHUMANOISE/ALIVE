'use client'

import MockWidget from '@/components/MockWidget'
import Panel from '@/components/Panel'
import PixelSprite from '@/components/PixelSprite'
import Reveal from '@/components/Reveal'
import { Accents, HueTile, Pips, Swatch, TabCard } from '@/components/ui'
import { useCopy } from '@/components/LangProvider'
import { badgeFace, fills } from '@/lib/hues'

/** 01 - the front door: what the app is, and the download. */
export function SectionHero() {
  const copy = useCopy()

  return (
    <section id="existence" className="px-5 py-16 md:px-12 md:py-24">
      <div className="mx-auto grid w-full max-w-5xl items-center gap-10 md:grid-cols-12">
        <div className="md:col-span-7">
          <Reveal className="flex items-center gap-3">
            {/* Amber invites; gold pays out. The front door is an invitation. */}
            <HueTile fill={fills.amber} mark="hourglass" size={36} />
            <span className={`badge ${badgeFace('pink')}`}>{copy.hero.badge}</span>
          </Reveal>

          <Reveal delay={60}>
            <h1 className="mt-6 font-display text-pixel">
              {copy.hero.title.map((line) => (
                <span key={line} className="block">
                  {line}
                </span>
              ))}
            </h1>
          </Reveal>

          <Reveal delay={120}>
            <p className="mt-7 max-w-xl font-head text-xl leading-snug md:text-2xl">
              {copy.hero.lead}
            </p>
          </Reveal>

          <Reveal delay={180}>
            <p className="mt-5 max-w-xl text-lg leading-relaxed text-ink-secondary">
              {copy.hero.body}
            </p>
          </Reveal>

          <Reveal delay={240}>
            <div className="mt-9 flex flex-wrap items-center gap-4">
              <a href="#download" className="btn btn-primary">
                {copy.hero.cta}
              </a>
              <span className="text-sm text-ink-secondary">{copy.hero.ctaNote}</span>
              <Accents seed={0} className="ml-auto" />
            </div>
          </Reveal>
        </div>

        <Reveal delay={200} className="md:col-span-5">
          <div className="flex justify-center">
            <PixelSprite name="wave" size={220} label={copy.hero.face} className="max-w-full" />
          </div>
        </Reveal>
      </div>
    </section>
  )
}

/**
 * 02 - three things that run out, in the colour that means running out.
 *
 * The three cards wear one hue rather than three: red is the beat's whole claim,
 * and a row of matching tabs is what lets the eye count three of one thing.
 */
export function SectionLoop() {
  const copy = useCopy()

  return (
    <Panel
      id="loop"
      badge={copy.loop.badge}
      tone="red"
      title={copy.loop.title}
      lead={copy.loop.lead}
      face="unaware"
    >
      <div className="grid gap-5 md:grid-cols-3">
        {copy.loop.items.map((item, index) => (
          <Reveal key={item.name} delay={index * 80}>
            <TabCard fill={fills.red} title={item.name} body={item.body} />
          </Reveal>
        ))}
      </div>

      <Reveal delay={260}>
        <div className="mt-8 flex flex-wrap items-center gap-4">
          {/* Three of them, shown as three blocks: the shape counts, the sentence
              beside it stays the authority. */}
          <Pips filled={copy.loop.items.length} total={copy.loop.items.length} fill={fills.red} />
          <p className="max-w-2xl text-sm leading-relaxed text-ink-muted">{copy.loop.note}</p>
        </div>
      </Reveal>
    </Panel>
  )
}

/** 03 - one real day, shown as the widget shows it. Gold: what the app hands out. */
export function SectionAwareness() {
  const copy = useCopy()
  const factFills = [fills.blue, fills.gold, fills.forest]

  return (
    <Panel
      id="awareness"
      badge={copy.awareness.badge}
      tone="gold"
      title={copy.awareness.title}
      lead={copy.awareness.lead}
      face="thinking"
    >
      <Reveal>
        <p className="max-w-2xl text-lg leading-relaxed text-ink-secondary">
          {copy.awareness.body}
        </p>
      </Reveal>

      <div className="mt-10 grid items-start gap-6 md:grid-cols-12">
        <Reveal className="md:col-span-6">
          <MockWidget
            label={copy.awareness.meter.label}
            figure={copy.awareness.title}
            allowance={copy.awareness.meter.allowance}
            screen={copy.awareness.meter.screen}
            apps={copy.awareness.meter.apps}
            face="tired"
          />
        </Reveal>

        <div className="space-y-4 md:col-span-6">
          {copy.awareness.facts.map((fact, index) => (
            <Reveal key={fact.name} delay={index * 80}>
              <article className="card-sm p-4">
                <div className="flex items-center gap-3">
                  {/* Budget, reward, and what ALIVE offers: three different facts,
                      so three different colours, none of them a mood. */}
                  <Swatch fill={factFills[index % factFills.length]} />
                  <h3 className="font-head font-bold">{fact.name}</h3>
                </div>
                <p className="mt-2 text-ink-secondary">{fact.body}</p>
              </article>
            </Reveal>
          ))}
        </div>
      </div>
    </Panel>
  )
}
