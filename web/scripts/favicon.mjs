/**
 * The site's tab icon.
 *
 * Search Console made the gap visible: a page with no icon has none in the
 * results either, so Google labelled the site with the host's name instead of
 * ALIVE's. The fix is not a new drawing - it is the hourglass the app already
 * ships as its launcher foreground and the site already draws in its header,
 * lifted straight out of `lib/sprites.ts` so the three can never disagree.
 *
 *   node web/scripts/favicon.mjs
 *
 * Writes three PNGs into `public/`, which is where the export copies them from:
 * 32 for the tab, 180 for the iOS home screen, 512 for anything that wants a
 * bigger square.
 */
import { readFileSync, writeFileSync } from 'node:fs'
import { deflateSync } from 'node:zlib'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const here = dirname(fileURLToPath(import.meta.url))
const web = join(here, '..')

const PAPER = '#F5F1E8'
const ART = 16

/**
 * The sprite source is a TS module, so it is read as text: each layer's colour
 * is found, everything up to the next colour belongs to it, and every
 * `[x, y, w, h]` in that slice is painted. Reading it as data instead of
 * importing keeps this script free of a TS transform.
 *
 * The first version of this parse captured `cells: [` up to the first closing
 * bracket - which is the end of the first *cell* - so it painted nothing and
 * still wrote a perfectly valid, completely blank PNG. Hence two guards: the
 * cell count is asserted, and a zero would now throw instead of shipping cream.
 */
function hourglass() {
  const source = readFileSync(join(web, 'lib/sprites.ts'), 'utf8')
  const start = source.indexOf('hourglass: {')
  if (start === -1) throw new Error('lib/sprites.ts: no hourglass sprite')
  const end = source.indexOf('\n  },', start)
  if (end === -1) throw new Error('lib/sprites.ts: hourglass block never closed')

  const grid = Array.from({ length: ART }, () => Array(ART).fill(PAPER))
  const block = source.slice(start, end)
  const marks = [...block.matchAll(/colour:\s*'(#[0-9A-Fa-f]{6})'/g)]
  if (marks.length === 0) throw new Error('lib/sprites.ts: hourglass has no layers')

  let painted = 0
  marks.forEach((mark, index) => {
    const colour = mark[1]
    const from = mark.index + mark[0].length
    const to = index + 1 < marks.length ? marks[index + 1].index : block.length
    for (const [, x, y, w, h] of block
      .slice(from, to)
      .matchAll(/\[(\d+),\s*(\d+),\s*(\d+),\s*(\d+)\]/g)) {
      for (let j = +y; j < +y + +h; j++) {
        for (let i = +x; i < +x + +w; i++) {
          grid[j][i] = colour
          painted++
        }
      }
    }
  })

  const expected = ART * ART * 0.15
  if (painted < expected) {
    throw new Error(`hourglass parsed as ${painted} pixels, which cannot be the drawing`)
  }

  /**
   * The sprite keeps empty rows above and below the drawing, because it is sized
   * for a 16px grid rather than for this picture. Centring the grid would seat
   * the hourglass high in the square; the drawing's own bounds get centred instead.
   */
  const box = { x0: ART, y0: ART, x1: -1, y1: -1 }
  grid.forEach((row, y) =>
    row.forEach((hex, x) => {
      if (hex === PAPER) return
      box.x0 = Math.min(box.x0, x)
      box.y0 = Math.min(box.y0, y)
      box.x1 = Math.max(box.x1, x)
      box.y1 = Math.max(box.y1, y)
    })
  )
  if (box.x1 < box.x0 || box.y1 < box.y0) throw new Error('hourglass drew no bounds')

  return { grid, box }
}

const crcTable = Array.from({ length: 256 }, (_, n) => {
  let c = n
  for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1
  return c >>> 0
})

const chunk = (type, body) => {
  const len = Buffer.alloc(4)
  len.writeUInt32BE(body.length)
  const head = Buffer.concat([Buffer.from(type, 'ascii'), body])
  let c = 0xffffffff
  for (const byte of head) c = crcTable[(c ^ byte) & 0xff] ^ (c >>> 8)
  const tail = Buffer.alloc(4)
  tail.writeUInt32BE((c ^ 0xffffffff) >>> 0)
  return Buffer.concat([len, head, tail])
}

const rgb = (hex) => [
  parseInt(hex.slice(1, 3), 16),
  parseInt(hex.slice(3, 5), 16),
  parseInt(hex.slice(5, 7), 16)
]

function png(pixelAt, size) {
  const stride = size * 3
  const raw = Buffer.alloc((stride + 1) * size)
  const cache = new Map()
  for (let y = 0; y < size; y++) {
    raw[y * (stride + 1)] = 0
    for (let x = 0; x < size; x++) {
      const hex = pixelAt(x, y)
      let values = cache.get(hex)
      if (!values) {
        values = rgb(hex)
        cache.set(hex, values)
      }
      const o = y * (stride + 1) + 1 + x * 3
      raw[o] = values[0]
      raw[o + 1] = values[1]
      raw[o + 2] = values[2]
    }
  }

  const ihdr = Buffer.alloc(13)
  ihdr.writeUInt32BE(size, 0)
  ihdr.writeUInt32BE(size, 4)
  ihdr[8] = 8
  ihdr[9] = 2

  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', ihdr),
    chunk('IDAT', deflateSync(raw, { level: 9 })),
    chunk('IEND', Buffer.alloc(0))
  ])
}

const { grid: art, box } = hourglass()
const artW = box.x1 - box.x0 + 1
const artH = box.y1 - box.y0 + 1

/** The drawing centred on the app's own cream, in whole pixels only. */
function icon(size) {
  const scale = Math.floor((size * 0.86) / Math.max(artW, artH))
  if (scale < 1) throw new Error(`size ${size} leaves no whole-pixel scale for ${artW}x${artH} art`)
  const marginX = Math.floor((size - artW * scale) / 2)
  const marginY = Math.floor((size - artH * scale) / 2)
  return png((x, y) => {
    const i = box.x0 + Math.floor((x - marginX) / scale)
    const j = box.y0 + Math.floor((y - marginY) / scale)
    if (i < box.x0 || i > box.x1 || j < box.y0 || j > box.y1) return PAPER
    return art[j][i]
  }, size)
}

for (const [name, size] of [
  ['icon-32.png', 32],
  ['icon-180.png', 180],
  ['icon-512.png', 512]
]) {
  const file = join(web, 'public', name)
  writeFileSync(file, icon(size))
  console.log(`${name}  ${size}x${size}`)
}
