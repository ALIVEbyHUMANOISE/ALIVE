'use client'

import MockWidget from '@/components/MockWidget'
import Panel from '@/components/Panel'
import PixelSprite from '@/components/PixelSprite'
import Reveal from '@/components/Reveal'
import { useCopy } from '@/components/LangProvider'

/** 01 - the front door: what the app is, and the download. */
export function SectionHero() {
  const copy = useCopy()

  return (
    <section id="existence" className="px-5 py-16 md:px-12 md:py-24">
      <div className="mx-auto grid w-full max-w-5xl items-center gap-10 md:grid-cols-12">
        <div className="md:col-span-7">
          <Reveal>
            <span className="badge">{copy.hero.badge}</span>
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

/** 02 - the loop, as three cards rather than three bullet points. */
export function SectionLoop() {
  const copy = useCopy()

  return (
    <Panel
      id="loop"
      badge={copy.loop.badge}
      title={copy.loop.title}
      lead={copy.loop.lead}
      face="unaware"
    >
      <div className="grid gap-5 md:grid-cols-3">
        {copy.loop.items.map((item, index) => (
          <Reveal key={item.name} delay={index * 80}>
            <article className="card h-full p-5">
              <h3 className="font-head text-lg font-bold">{item.name}</h3>
              <div className="rule mt-3" />
              <p className="mt-3 leading-relaxed text-ink-secondary">{item.body}</p>
            </article>
          </Reveal>
        ))}
      </div>

      <Reveal delay={260}>
        <p className="mt-8 max-w-2xl text-sm leading-relaxed text-ink-muted">{copy.loop.note}</p>
      </Reveal>
    </Panel>
  )
}

/** 03 - one real day, shown as the widget shows it. */
export function SectionAwareness() {
  const copy = useCopy()

  return (
    <Panel id="awareness" badge={copy.awareness.badge} title={copy.awareness.title} lead={copy.awareness.lead} face="thinking">
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
                <h3 className="font-head font-bold">{fact.name}</h3>
                <p className="mt-1 text-ink-secondary">{fact.body}</p>
              </article>
            </Reveal>
          ))}
        </div>
      </div>
    </Panel>
  )
}
