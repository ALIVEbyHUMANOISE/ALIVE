/**
 * The one address the site is known by, and the facts that live outside the page.
 *
 * Canonical links, the sitemap, the picture a chat app previews and the
 * structured data all have to be absolute URLs: `/privacy` is this page on one
 * host and a stranger's page on another. So the host belongs here, and whoever
 * deploys sets it:
 *
 *   NEXT_PUBLIC_SITE_URL=https://<host> npm run build
 *
 * Without it the build still renders, but every absolute URL it advertises
 * points at localhost - which is exactly what a crawler would then store as
 * this site's canonical address. scripts/guard.mjs stops a production build
 * that forgot to set it.
 */
const fromEnv = process.env.NEXT_PUBLIC_SITE_URL?.replace(/\/+$/, '')

export const siteUrl = fromEnv ?? 'http://localhost:3001'

/** True while nothing was set, so the build can say so out loud. */
export const hostIsPlaceholder = fromEnv === undefined

export const brand = 'ALIVE'

/** The same sentence the app's own first screen makes. One place, one wording. */
export const description =
  'ALIVE menghitung waktu hiburan di HP-mu, menguncinya saat jatah habis, dan memberi ruang untuk fokus. Semuanya tetap di perangkatmu.'

/** The address a user can write to. Play requires a public contact, and this is it. */
export const contactEmail = 'alivebyhumanoise@gmail.com'

/**
 * The pages a crawler is allowed to find, with the trailing slash the config
 * (`trailingSlash: true`) actually serves them under.
 */
export const pages: { path: string; changeFrequency: 'weekly' | 'yearly'; priority: number }[] = [
  { path: '/', changeFrequency: 'weekly', priority: 1 },
  { path: '/privacy/', changeFrequency: 'yearly', priority: 0.6 }
]

/**
 * What Google calls a rich result: one SoftwareApplication entry that says the
 * price is zero, the OS, and what the thing does, in the words a listing would
 * use. Ratings are deliberately absent - there is no store yet, and an invented
 * aggregateRating is the fastest way to lose a crawler's trust.
 */
export const applicationJsonLd = {
  '@context': 'https://schema.org',
  '@type': 'SoftwareApplication',
  name: brand,
  url: `${siteUrl}/`,
  description,
  image: `${siteUrl}/images/og-preview.png`,
  applicationCategory: 'LifestyleApplication',
  operatingSystem: 'Android 8.0 (API 26) dan yang lebih baru',
  inLanguage: ['id', 'en'],
  offers: [{ '@type': 'Offer', price: '0', priceCurrency: 'IDR' }],
  featureList: [
    'Menghitung waktu hiburan per app, hanya app yang benar-benar dibatasi',
    'Jatah harian yang kamu tetapkan sendiri, dan dia yang menutup app-nya',
    'Sesi fokus dan misi harian untuk jam yang tersisa',
    'Widget angka hari itu di home screen',
    'Tanpa akun, tanpa server, dan tanpa izin internet di Android'
  ],
  author: { '@type': 'Organization', name: brand, url: siteUrl, email: `mailto:${contactEmail}` }
}
