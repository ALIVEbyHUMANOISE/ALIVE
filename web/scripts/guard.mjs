/**
 * The site's own colour guard.
 *
 * The bug this catches is invisible in every other way: Tailwind deletes any
 * utility it cannot find as literal text in a scanned file, so a colour written
 * in `lib/` but not in `content` compiles, ships and renders white - and nobody
 * gets an error, only a beige website. This ran on the build once already and
 * five classes quietly vanished for a whole pass.
 *
 * So it does not look at intent. It reads the class names out of the registry and
 * the component layer, reads the CSS the build actually emitted, and fails if a
 * name is in one file and not the other. Run after `next build`:
 *
 *   npm run guard
 */
import { readFileSync, readdirSync } from 'node:fs'
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

const problems = [
  ...missing.map(({ name, file }) => `${file}: .${name} is used but the built CSS never defines it`),
  ...unmapped.map((name) => `tailwind.config.ts: --alive-${name.replace('cat-', 'cat_')} has no colour entry`)
]

if (problems.length) {
  console.error('guard: the site lost colours between source and build\n' + problems.join('\n'))
  process.exit(1)
}

const hues = [...tokens.matchAll(/--alive-cat_\w+/g)].length
console.log(`guard: ${required.length} colour classes and ${hues} category hues all rendered`)
