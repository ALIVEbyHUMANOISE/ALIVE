'use client'

import Header from './Header'
import { SectionAwareness, SectionHero, SectionLoop } from './sections/Story'
import {
  SectionChoice,
  SectionDownload,
  SectionRealization,
  SectionRoom,
  SectionTakeover,
  SiteFooter
} from './sections/Answer'
import { useCopy } from './LangProvider'

/**
 * The whole page, in the app's order of speaking: the day, what eats it, the
 * number that proves it, the picture of who is driving, the choice, the room that
 * opens up, and the download.
 */
export default function Site() {
  const copy = useCopy()

  return (
    <>
      <a
        href="#existence"
        className="sr-only focus:not-sr-only focus:absolute focus:left-4 focus:top-4 focus:z-50 focus:bg-white focus:px-3 focus:py-2"
      >
        {copy.meta.skip}
      </a>

      <Header />

      <main>
        <SectionHero />
        <SectionLoop />
        <SectionAwareness />
        <SectionRealization />
        <SectionTakeover />
        <SectionChoice />
        <SectionRoom />
        <SectionDownload />
      </main>

      <SiteFooter />
    </>
  )
}
