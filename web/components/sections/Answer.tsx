'use client'

import Image from 'next/image'
import Panel from '@/components/Panel'
import PixelSprite from '@/components/PixelSprite'
import Reveal from '@/components/Reveal'
import { Accents, HueTile, Pips, RewardChip, Swatch, TabCard, TimeBar } from '@/components/ui'
import { useCopy } from '@/components/LangProvider'
import { badgeFace, categories, fills } from '@/lib/hues'

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
        {/* The one MAJOR of this beat, and the one card here that wears a colour
            instead of paper: the promise is the news, and the app gives a promise
            the resting green it gives a day that held. */}
        <article className="card-major h-full bg-soft-forest p-6 md:p-8">
          <div className="flex flex-wrap items-start justify-between gap-4">
            <h3 className="font-display text-pixel-sm">{copy.realization.privacy.title}</h3>
            <PixelSprite name="shield" size={44} />
          </div>

          <div className="rule mt-5" />

          <p className="mt-5 max-w-2xl text-lg leading-relaxed">{copy.realization.privacy.body}</p>

          <div className="mt-6 flex flex-wrap gap-2">
            {copy.realization.privacy.marks.map((mark) => (
              <span key={mark} className="badge flex items-center gap-2">
                {/* Three promises, one colour: forest is what ALIVE is offering,
                    and none of these three is a score. */}
                <Swatch fill={fills.forest} size="h-3 w-3" />
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
        <div className="rule mx-auto mt-4 max-w-md" />
        <p className="mx-auto mt-4 max-w-xl leading-relaxed text-ink-secondary">
          {copy.takeover.note}
        </p>
        <Accents seed={4} className="mt-6 justify-center" />
      </Reveal>
    </section>
  )
}

/**
 * 06 - what actually happens when the allowance runs out.
 *
 * The three steps wear the three colours the app gives those states: red for the
 * time that is running out, the darker red for the moment it is gone, and forest
 * for the day ALIVE still lets you keep. The bars are the app's own `timeBar`
 * shape, so a step is recognised before it is read.
 */
export function SectionChoice() {
  const copy = useCopy()
  const stepFills = [fills.red, fills.redDeep, fills.forest]

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
            <TabCard fill={stepFills[index % stepFills.length]} title={step.name} body={step.body}>
              {/* One object per step, and each one says the step's own fact: a bar
                  almost spent, a bar empty, a chain with nothing in it yet. */}
              <div className="mt-4">
                {index === 0 && <TimeBar remaining={1} limit={10} fill={fills.red} />}
                {index === 1 && <TimeBar remaining={0} limit={10} fill={fills.red} />}
                {index === 2 && <Pips filled={0} fill={fills.gold} />}
              </div>
            </TabCard>
          </Reveal>
        ))}
      </div>
    </Panel>
  )
}

/**
 * 07 - the character that does the closing, and what it does when it is
 * switched off.
 *
 * The one section where the argument is a face rather than a number, so the
 * poster gets the width and the words sit beside it. It is also the honest
 * counterweight to the section above: the app cannot stop anyone turning the
 * guard off, so it says what the Guardian becomes instead - and the note at the
 * bottom refuses to call a dismissible popup a lock.
 */
export function SectionGuardian() {
  const copy = useCopy()

  return (
    <section id="guardian" className="border-t-[3px] border-ink px-5 py-16 md:px-12 md:py-24">
      <div className="mx-auto w-full max-w-5xl">
        <Reveal className="flex items-center gap-3">
          {/* The app's own sprite, so the poster below is unmistakably the same
              character and not an illustration that happens to share a palette. */}
          <PixelSprite name="guardian_alert" size={32} />
          <span className={`badge ${badgeFace('red')}`}>{copy.guardian.badge}</span>
        </Reveal>

        <div className="mt-10 grid items-center gap-8 md:grid-cols-12">
          <Reveal delay={60} className="md:col-span-6">
            <Image
              src="/images/guardian.png"
              alt={copy.guardian.alt}
              width={768}
              height={768}
              className="mx-auto h-auto w-full max-w-[420px] rounded-card border-[3px] border-ink shadow-raise"
            />
          </Reveal>

          <div className="md:col-span-6">
            <Reveal delay={120}>
              <h2 className="font-display text-pixel-sm">
                {copy.guardian.title.map((line) => (
                  <span key={line} className="block">
                    {line}
                  </span>
                ))}
              </h2>
            </Reveal>

            <Reveal delay={160}>
              <p className="mt-5 font-head text-xl leading-snug">{copy.guardian.lead}</p>
            </Reveal>

            <Reveal delay={200}>
              <p className="mt-4 leading-relaxed text-ink-secondary">{copy.guardian.body}</p>
            </Reveal>

            <div className="mt-6 space-y-3">
              {copy.guardian.places.map((place, index) => (
                <Reveal key={place.name} delay={240 + index * 80}>
                  <article className="card-sm p-4">
                    <div className="flex items-center gap-3">
                      {/* One colour for all three: they are not three ideas, they
                          are the same promise arriving three ways. */}
                      <Swatch fill={fills.red} />
                      <h3 className="font-head font-bold">{place.name}</h3>
                    </div>
                    <p className="mt-2 text-sm leading-relaxed text-ink-secondary">{place.body}</p>
                  </article>
                </Reveal>
              ))}
            </div>

            <Reveal delay={500}>
              <p className="mt-6 text-sm leading-relaxed text-ink-muted">{copy.guardian.honest}</p>
            </Reveal>
          </div>
        </div>
      </div>
    </section>
  )
}

/** 08 - the hours it saved have somewhere to go, and a board to go on. */
export function SectionRoom() {
  const copy = useCopy()
  const featureFills = [fills.blue, fills.gold, fills.forest, fills.amber]
  const marks = ['laptop', 'target', 'chart', 'hourglass'] as const

  // The marks the app's board actually draws - a hue and its shape always arrive
  // together, so this row is one sentence rather than a colour test.
  const board = [
    categories.study,
    categories.reading,
    categories.exercise,
    categories.walking,
    categories.cleaning,
    categories.creative,
    categories.coding,
    categories.writing
  ]

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
            <TabCard
              fill={featureFills[index % featureFills.length]}
              title={feature.name}
              body={feature.body}
              mark={marks[index % marks.length]}
            >
              {index === 1 && (
                <div className="mt-4 flex flex-wrap gap-2">
                  {board.map((category) => (
                    <HueTile
                      key={category.fill}
                      fill={category.fill}
                      mark={category.mark}
                      size={30}
                    />
                  ))}
                </div>
              )}
            </TabCard>
          </Reveal>
        ))}
      </div>
    </Panel>
  )
}

/**
 * 08 - the download, said honestly.
 *
 * One MAJOR, and it wears the critter's own pink: on this page the offer is the
 * news, and pink is the face the app reserves for the thing it raised without
 * being asked. A caption on a saturated face is full ink - secondary ink on pink
 * measures 3.9:1, so the app prints the sentence in black instead of grey.
 */
export function SectionDownload() {
  const copy = useCopy()
  const permissionFills = [fills.blue, fills.forest]

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
          <div className="card-major bg-pink p-6 md:p-8">
            <div className="flex flex-wrap items-baseline justify-between gap-3">
              <span className="font-display text-pixel-sm">{copy.download.file.name}</span>
              <RewardChip value={copy.download.file.size} />
            </div>

            <div className="rule mt-5" />

            <div className="mt-6 flex flex-wrap items-center gap-4">
              <a href="/apk/alive-debug.apk" download className="btn btn-primary">
                {copy.download.cta}
              </a>
              <span className="text-sm">{copy.download.file.requires}</span>
            </div>

            <p className="mt-6 max-w-xl text-sm leading-relaxed">
              {copy.download.honest}
            </p>

            <Accents seed={5} className="mt-6" size={14} />
          </div>
        </Reveal>

        <Reveal delay={120} className="md:col-span-5">
          <div className="card-sm h-full p-5">
            <h3 className="font-head font-bold">{copy.download.permissions.title}</h3>
            <div className="mt-4 space-y-4">
              {[copy.download.permissions.usage, copy.download.permissions.access].map(
                (permission, index) => (
                  <div key={permission.name} className="flex gap-3">
                    {/* Counting is the blue of work; closing is the green of an
                        offer. Both named in words underneath, as always. */}
                    <Swatch fill={permissionFills[index % permissionFills.length]} />
                    <div>
                      <p className="font-medium">{permission.name}</p>
                      <p className="text-ink-secondary">{permission.body}</p>
                    </div>
                  </div>
                )
              )}
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
        <Accents seed={1} size={14} className="ml-auto" />
        <a href="#existence" className="text-sm text-ink-secondary underline">
          {copy.meta.toTop}
        </a>
      </div>
      <p className="mx-auto mt-4 w-full max-w-5xl text-sm text-ink-muted">{copy.footer.made}</p>
    </footer>
  )
}
