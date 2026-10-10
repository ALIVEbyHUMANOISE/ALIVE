/**
 * Copies the app's debug build into the site's `public/` folder.
 *
 * The download button points at a file, not at a service, so the file has to be
 * there before the site is deployed. This fails loudly when the app has not been
 * built: a deploy that quietly ships a dead download link is worse than one that
 * stops.
 */
import { copyFileSync, mkdirSync, readFileSync, statSync, writeFileSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const here = dirname(fileURLToPath(import.meta.url))
const repo = resolve(here, '../..')
const source = join(repo, 'app/build/outputs/apk/debug/app-debug.apk')
const target = join(repo, 'web/public/apk/alive-debug.apk')

let built
try {
  built = readFileSync(join(repo, 'app/build/outputs/apk/debug/output-metadata.json'), 'utf8')
} catch {
  console.error(`No debug build found at ${source}. Run: ./gradlew assembleDebug`)
  process.exit(1)
}

const versionCode = JSON.parse(built).elements?.[0]?.versionCode ?? 'unknown'

mkdirSync(dirname(target), { recursive: true })
copyFileSync(source, target)

const megabytes = (statSync(target).size / 1024 / 1024).toFixed(1)
writeFileSync(
  join(repo, 'web/public/apk/BUILD.txt'),
  `alive-debug.apk — versionCode ${versionCode}, ${megabytes} MB\n`
)

console.log(`copied versionCode ${versionCode} to web/public/apk/alive-debug.apk (${megabytes} MB)`)
