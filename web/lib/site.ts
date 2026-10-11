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
/**
 * The host of a URL is case-insensitive; its path is not. GitHub hands over the
 * account login with its capitals intact (`ALIVEbyHUMANOISE`), and Pages answers
 * on either spelling - so the host is folded to lowercase here and the path is
 * left exactly as the repository spells it. Doing it in the workflow is not an
 * option: the Actions expression language has no case function at all.
 */
function normalize(raw: string): string {
  const url = new URL(raw)
  url.hostname = url.hostname.toLowerCase()
  return url.href.replace(/\/+$/, '')
}

const fromEnv = process.env.NEXT_PUBLIC_SITE_URL ? normalize(process.env.NEXT_PUBLIC_SITE_URL) : undefined

export const siteUrl = fromEnv ?? 'http://localhost:3001'

/** True while nothing was set, so the build can say so out loud. */
export const hostIsPlaceholder = fromEnv === undefined

/**
 * A GitHub Pages *project* site is served from under `/repo/`, and Next then has
 * to ask for its own files from under that same folder. The prefix is therefore
 * read out of the address rather than set separately: one value to get wrong
 * instead of two that can disagree. `next.config.mjs` derives the same string
 * from the same variable - it cannot import this file, being plain JS.
 */
export const basePath =
  hostIsPlaceholder || new URL(siteUrl).pathname === '/'
    ? ''
    : new URL(siteUrl).pathname.replace(/\/$/, '')

/**
 * The download itself lives on GitHub Releases, not inside the site's own
 * folder. Two reasons: an APK rebuilt every week is a new 5 MB blob in the
 * deploy every week, and a release page is where a beta user can read which
 * version they are about to install. `latest/download` means this link cannot
 * go stale - it follows whatever is the newest release.
 */
export const apkUrl = process.env.NEXT_PUBLIC_APK_URL

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
  downloadUrl: apkUrl,
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
