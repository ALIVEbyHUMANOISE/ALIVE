'use client'

import PixelSprite from './PixelSprite'
import { useCopy, useLang } from './LangProvider'
import { langs } from '@/lib/copy'

/** The wordmark, the language, and the only button that matters. */
export default function Header() {
  const copy = useCopy()
  const { lang, setLang } = useLang()

  return (
    <header className="sticky top-0 z-50 border-b-[3px] border-ink bg-paper">
      <div className="mx-auto flex w-full max-w-5xl items-center gap-3 px-5 py-3 md:px-12">
        <PixelSprite name="hourglass" size={22} />

        <span className="font-display text-lg leading-none">{copy.meta.brand}</span>
        <span className="hidden text-sm text-ink-secondary md:inline">{copy.meta.tagline}</span>

        <div className="ml-auto flex items-center gap-2">
          <div
            className="flex items-center rounded-step border-2 border-ink"
            role="group"
            aria-label={copy.nav.langLabel}
          >
            {langs.map((option) => {
              const active = option.code === lang
              return (
                <button
                  key={option.code}
                  type="button"
                  onClick={() => setLang(option.code)}
                  aria-pressed={active}
                  className={`px-2 py-1 font-display text-[10px] leading-none ${
                    active ? 'bg-gold-mid text-ink' : 'bg-white text-ink-secondary'
                  }`}
                >
                  {option.label}
                </button>
              )
            })}
          </div>

          <a href="#download" className="btn btn-primary px-4 py-2 text-sm">
            {copy.nav.download}
          </a>
        </div>
      </div>
    </header>
  )
}
