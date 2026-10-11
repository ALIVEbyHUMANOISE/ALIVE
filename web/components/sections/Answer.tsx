'use client'

import Image from 'next/image'
import Panel from '@/components/Panel'
import PixelSprite from '@/components/PixelSprite'
import Reveal from '@/components/Reveal'
import { useCopy } from '@/components/LangProvider'
import { face, rowFace } from '@/lib/tones'
import type { SpriteName } from '@/lib/sprites'

/** 04 - the number is not advice; the privacy promise is the reason to trust it. */
export function SectionRealization() {
  const copy = useCopy()

  return (
    <Panel
      id="realization"
      badge={copy.realization.badge}
      tone="forest"
      title={copy.realization.title}
      lead={copy.realization.lead}
      body={copy.realization.body}
      face="confused"
    >
      <Reveal>
        <article className={`card p-6 md:p-8 ${face('forest')}`}>
          <div className="flex flex-wrap items-start justify-between gap-4">
            <h3 className="font-display text-pixel-sm">{copy.realization.privacy.title}</h3>
            <PixelSprite name="shield" size={44} />
          </div>

          <div className="rule mt-5" />

          <p className="mt-5 max-w-2xl text-lg leading-relaxed text-ink-secondary">
            {copy.realization.privacy.body}
          </p>

          <div className="mt-6 flex flex-wrap gap-2">
            {copy.realization.privacy.marks.map((mark) => (
              <span key={mark} className="badge">
                {mark}
              </span>
            ))}
          </div>
        </article>
      </Reveal>
    </Panel>
  )
}

/**
 * 05 - the poster, at the size of the argument.
 *
 * It gets a screen to itself because that is what the picture is for: you scroll
 * past a card, you do not scroll past a face leaning over you. Nothing is
 * cropped, either - the lettering is half the sentence, so the art is *fitted* to
 * the viewport rather than filled across it, and the ink frame keeps it inside
 * the same object language as every other card.
 */
export function SectionTakeover() {
  const copy = useCopy()

  return (
    <section
      id="takeover"
      className="flex min-h-[100svh] flex-col items-center justify-center gap-9 border-t-[3px] border-ink px-5 py-14 md:px-12"
    >
      <Reveal>
        <Image
          src="/images/takeover.png"
          alt={copy.takeover.alt}
          width={992}
          height={1074}
          className="h-auto max-h-[68svh] w-auto max-w-[88vw] rounded-card border-[3px] border-ink shadow-raise"
        />
      </Reveal>

      <Reveal delay={120} className="px-2 text-center">
        <p className="font-display text-pixel-sm">{copy.takeover.line}</p>
        <p className="mx-auto mt-4 max-w-xl leading-relaxed text-ink-secondary">
          {copy.takeover.note}
        </p>
      </Reveal>
    </section>
  )
}

/** 05 - what actually happens when the allowance runs out. Each step wears the
 * colour the app gives that state, because the three are three different facts:
 * a warning, a loss, and a day that is allowed to be quiet. */
export function SectionChoice() {
  const copy = useCopy()
  const stepTones = ['gold', 'red', 'forest'] as const

  return (
    <Panel
      id="choice"
      badge={copy.choice.badge}
      tone="gold"
      title={copy.choice.title}
      lead={copy.choice.lead}
      body={copy.choice.body}
      face="angry"
    >
      <div className="grid gap-5 md:grid-cols-3">
        {copy.choice.steps.map((step, index) => (
          <Reveal key={step.name} delay={index * 80}>
            <article className={`card h-full p-5 ${face(stepTones[index % stepTones.length])}`}>
              <div className="flex items-center justify-between gap-3">
                <h3 className="font-head text-lg font-bold">{step.name}</h3>
                <span className="font-display text-pixel-xs text-ink-muted">
                  0{index + 1}
                </span>
              </div>
              <div className="rule mt-3" />
              <p className="mt-3 leading-relaxed text-ink-secondary">{step.body}</p>
            </article>
          </Reveal>
        ))}
      </div>
    </Panel>
  )
}

/** 06 - the hours it saves have somewhere to go. Blue: what was worked for. */
export function SectionRoom() {
  const copy = useCopy()
  const marks: SpriteName[] = ['laptop', 'target', 'chart', 'hourglass']

  return (
    <Panel
      id="room"
      badge={copy.room.badge}
      tone="blue"
      title={copy.room.title}
      lead={copy.room.lead}
      body={copy.room.body}
      face="happy"
    >
      <div className="grid gap-5 md:grid-cols-2">
        {copy.room.features.map((feature, index) => (
          <Reveal key={feature.name} delay={index * 80}>
            <article className={`card h-full p-5 ${rowFace(index + 2)}`}>
              <div className="flex items-center gap-3">
                <PixelSprite name={marks[index]} size={28} />
                <h3 className="font-head text-lg font-bold">{feature.name}</h3>
              </div>
              <div className="rule mt-3" />
              <p className="mt-3 leading-relaxed text-ink-secondary">{feature.body}</p>
            </article>
          </Reveal>
        ))}
      </div>
    </Panel>
  )
}

/** 07 - the download, said honestly. The two cards stay white: on this page the
 * offer itself is the news, and news is the only thing allowed to be white. */
export function SectionDownload() {
  const copy = useCopy()

  return (
    <Panel
      id="download"
      badge={copy.download.badge}
      tone="pink"
      title={copy.download.title}
      lead={copy.download.lead}
      face="alive"
    >
      <div className="grid gap-6 md:grid-cols-12">
        <Reveal className="md:col-span-7">
          <div className="card p-6 md:p-8">
            <div className="flex flex-wrap items-baseline justify-between gap-3">
              <span className="font-display text-pixel-sm">{copy.download.file.name}</span>
              <span className="text-sm text-ink-secondary">
                {copy.download.file.size} · {copy.download.file.requires}
              </span>
            </div>

            <div className="rule mt-5" />

            <div className="mt-6 flex flex-wrap items-center gap-4">
              <a href="/apk/alive-debug.apk" download className="btn btn-primary">
                {copy.download.cta}
              </a>
              <span className="text-sm text-ink-secondary">{copy.download.file.requires}</span>
            </div>

            <p className="mt-6 max-w-xl text-sm leading-relaxed text-ink-muted">
              {copy.download.honest}
            </p>
          </div>
        </Reveal>

        <Reveal delay={120} className="md:col-span-5">
          <div className="card-sm h-full p-5">
            <h3 className="font-head font-bold">{copy.download.permissions.title}</h3>
            <div className="mt-4 space-y-4">
              {[copy.download.permissions.usage, copy.download.permissions.access].map((permission) => (
                <div key={permission.name}>
                  <p className="font-medium">{permission.name}</p>
                  <p className="text-ink-secondary">{permission.body}</p>
                </div>
              ))}
            </div>
          </div>
        </Reveal>
      </div>
    </Panel>
  )
}

export function SiteFooter() {
  const copy = useCopy()

  return (
    <footer className="border-t-[3px] border-ink px-5 py-10 md:px-12">
      <div className="mx-auto flex w-full max-w-5xl flex-wrap items-center gap-4">
        <PixelSprite name="hourglass" size={20} />
        <span className="font-display text-pixel-xs">{copy.footer.line}</span>
        <span className="text-sm text-ink-secondary">{copy.footer.sub}</span>
        <a href="#existence" className="ml-auto text-sm text-ink-secondary underline">
          {copy.meta.toTop}
        </a>
      </div>
      <p className="mx-auto mt-4 w-full max-w-5xl text-sm text-ink-muted">{copy.footer.made}</p>
    </footer>
  )
}
