import type { Metadata, Viewport } from 'next'
import localFont from 'next/font/local'
import './globals.css'
import { LangProvider } from '@/components/LangProvider'
import { applicationJsonLd, brand, description, siteUrl } from '@/lib/site'

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
  /**
   * Every relative URL below is resolved against this one at build time, which
   * is what turns "the poster" into an address a crawler or a chat preview can
   * actually fetch. Set NEXT_PUBLIC_SITE_URL when deploying; see lib/site.ts.
   */
  metadataBase: new URL(siteUrl),
  title: {
    default: 'ALIVE — sadari waktumu, kendalikan layarmu',
    template: `%s — ${brand}`
  },
  description,
  /** The address Google stores as this page's own, so a query string or a copy
   *  on another host cannot split the site's reputation across URLs. */
  alternates: { canonical: '/' },
  openGraph: {
    title: brand,
    description: 'Sadari waktumu. Kendalikan layarmu.',
    type: 'website',
    url: '/',
    siteName: brand,
    locale: 'id_ID',
    // The poster is the whole argument in one picture, so it is also what a
    // shared link shows. Chat apps crop to 1.91:1, hence the padded copy.
    images: [
      {
        url: '/images/og-preview.png',
        width: 1200,
        height: 630,
        alt: 'Poster pixel: monster HP bertaring dan bercakar mengangkangi karakter kecil ALIVE, dengan tulisan “Your Phone has Taken over your life”'
      }
    ]
  },
  twitter: {
    card: 'summary_large_image',
    title: brand,
    description: 'Sadari waktumu. Kendalikan layarmu.',
    images: ['/images/og-preview.png']
  },
  robots: { index: true, follow: true }
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
        {/* Said once, in the vocabulary a search engine reads, next to the
            human-readable page rather than instead of it. */}
        <script
          type="application/ld+json"
          dangerouslySetInnerHTML={{ __html: JSON.stringify(applicationJsonLd) }}
        />
        <LangProvider>{children}</LangProvider>
      </body>
    </html>
  )
}
