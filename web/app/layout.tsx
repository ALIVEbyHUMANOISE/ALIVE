import type { Metadata, Viewport } from 'next'
import localFont from 'next/font/local'
import './globals.css'
import { LangProvider } from '@/components/LangProvider'

/**
 * The three faces ALIVE actually uses, shipped from the same files the app
 * bundles. A website that loads a different Silkscreen is a website that drifts.
 */
const display = localFont({
  src: '../public/fonts/silkscreen_bold.ttf',
  variable: '--font-display',
  display: 'swap'
})

/** Both weights the app uses: a bold heading synthesised from a semibold file is a fake. */
const head = localFont({
  src: [
    { path: '../public/fonts/space_grotesk_semibold.ttf', weight: '600', style: 'normal' },
    { path: '../public/fonts/space_grotesk_bold.ttf', weight: '700', style: 'normal' }
  ],
  variable: '--font-head',
  display: 'swap'
})

const bodyRegular = localFont({
  src: [
    { path: '../public/fonts/manrope_regular.ttf', weight: '400', style: 'normal' },
    { path: '../public/fonts/manrope_medium.ttf', weight: '500', style: 'normal' },
    { path: '../public/fonts/manrope_bold.ttf', weight: '700', style: 'normal' }
  ],
  variable: '--font-body',
  display: 'swap'
})

export const metadata: Metadata = {
  title: 'ALIVE — sadari waktumu, kendalikan layarmu',
  description:
    'ALIVE menghitung waktu hiburan di HP-mu, menguncinya saat jatah habis, dan memberi ruang untuk fokus. Semuanya tetap di perangkatmu.',
  openGraph: {
    title: 'ALIVE',
    description: 'Sadari waktumu. Kendalikan layarmu.',
    type: 'website'
  }
}

export const viewport: Viewport = {
  themeColor: '#F5F1E8'
}

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html
      lang="id"
      className={`${display.variable} ${head.variable} ${bodyRegular.variable}`}
    >
      <body className="bg-paper font-body text-ink antialiased">
        <LangProvider>{children}</LangProvider>
      </body>
    </html>
  )
}
