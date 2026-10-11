/**
 * The site's own build guard.
 *
 * The first bug it catches is invisible in every other way: Tailwind deletes any
 * utility it cannot find as literal text in a scanned file, so a colour written
 * in `lib/` but not in `content` compiles, ships and renders white - and nobody
 * gets an error, only a beige website. This ran on the build once already and
 * five classes quietly vanished for a whole pass.
 *
 * So it does not look at intent. It reads the class names out of the registry and
 * the component layer, reads the CSS the build actually emitted, and fails if a
 * name is in one file and not the other.
 *
 * The second bug it catches is the same shape one layer up: a static export with
 * no host set still builds, still renders, and hands every crawler it meets a
 * canonical URL and a sitemap pointing at localhost. Indexed under that address,
 * a site is invisible under its own one. So the emitted SEO files are read back
 * and compared with the host the build was told to advertise.
 *
 * Run after `next build`:
 *
 *   NEXT_PUBLIC_SITE_URL=https://<host> npm run build
 */
import { existsSync, readFileSync, readdirSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const here = dirname(fileURLToPath(import.meta.url))
const web = join(here, '..')

const read = (path) => readFileSync(join(web, path), 'utf8')

/** Every `bg-` utility named in a source file, from any string or @apply. */
function bgClasses(text) {
  return [...new Set([...text.matchAll(/['"`\s](bg-[\w-]+)/g)].map((m) => m[1]))]
}

const sources = [
  { file: 'lib/hues.ts', text: read('lib/hues.ts') },
  { file: 'app/globals.css', text: read('app/globals.css') }
]

const required = sources.flatMap(({ file, text }) =>
  bgClasses(text).map((name) => ({ name, file }))
)

const cssDir = join(web, 'out/_next/static/css')
const css = readdirSync(cssDir)
  .filter((file) => file.endsWith('.css'))
  .map((file) => read(`out/_next/static/css/${file}`))
  .join('\n')

const missing = required.filter(({ name }) => !css.includes(`.${name}{`))

/**
 * The other half of the same mistake: a token the app has and the site cannot
 * reach. Every category hue in the generated palette must be mapped, or the
 * registry is offering classes Tailwind has never heard of.
 */
const tokens = read('app/tokens.css')
const config = read('tailwind.config.ts')
const unmapped = [...tokens.matchAll(/--alive-(cat_\w+)/g)]
  .map((m) => m[1].replace('cat_', 'cat-'))
  .filter((name) => !config.includes(`'${name}'`))

/** The files a crawler reads before it reads a single word of the page. */
const seoFiles = ['robots.txt', 'sitemap.xml', 'index.html']
const absent = seoFiles.filter((file) => !existsSync(join(web, 'out', file)))
const advertised = process.env.NEXT_PUBLIC_SITE_URL?.replace(/\/+$/, '')

const seo = []
if (absent.length) {
  seo.push(`out/: ${absent.join(', ')} was never emitted — nobody can be told where the site is`)
}
if (!advertised) {
  seo.push('build: NEXT_PUBLIC_SITE_URL is unset, so every absolute URL says localhost')
} else {
  for (const file of seoFiles) {
    if (absent.includes(file)) continue
    const text = readFileSync(join(web, 'out', file), 'utf8')
    if (!text.includes(advertised)) {
      seo.push(`out/${file}: never mentions ${advertised}`)
    }
    if (text.includes('://localhost')) {
      seo.push(`out/${file}: advertises a localhost address`)
    }
  }
}

const problems = [
  ...missing.map(({ name, file }) => `${file}: .${name} is used but the built CSS never defines it`),
  ...unmapped.map((name) => `tailwind.config.ts: --alive-${name.replace('cat-', 'cat_')} has no colour entry`),
  ...seo
]

if (problems.length) {
  console.error(
    'guard: the site lost something between source and build\n' +
      problems.join('\n') +
      '\n\nfix: NEXT_PUBLIC_SITE_URL=https://<host> npm run build'
  )
  process.exit(1)
}

const hues = [...tokens.matchAll(/--alive-cat_\w+/g)].length
console.log(
  `guard: ${required.length} colour classes and ${hues} category hues all rendered, at ${advertised}`
)
