'use client'

import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { dictionaries, type Copy, type Lang } from '@/lib/copy'

type Value = { lang: Lang; setLang: (lang: Lang) => void }

const LangContext = createContext<Value>({ lang: 'id', setLang: () => {} })

/** Indonesian until the visitor says otherwise, and the choice outlives the visit. */
export function LangProvider({ children }: { children: React.ReactNode }) {
  const [lang, setLang] = useState<Lang>('id')

  useEffect(() => {
    const saved = window.localStorage.getItem('alive-lang')
    if (saved === 'id' || saved === 'en') setLang(saved)
  }, [])

  useEffect(() => {
    document.documentElement.lang = lang
  }, [lang])

  const remember = useCallback((next: Lang) => {
    setLang(next)
    window.localStorage.setItem('alive-lang', next)
  }, [])

  const value = useMemo(() => ({ lang, setLang: remember }), [lang, remember])

  return <LangContext.Provider value={value}>{children}</LangContext.Provider>
}

export function useLang(): Value {
  return useContext(LangContext)
}

export function useCopy(): Copy {
  const { lang } = useContext(LangContext)
  return dictionaries[lang]
}
