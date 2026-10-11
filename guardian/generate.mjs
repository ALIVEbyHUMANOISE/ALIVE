/**
 * The Guardian — four poses, drawn on the same 16x16 grid as the critter.
 *
 * The character is authored here as readable rows rather than as hand-written
 * path data, for the same reason the poster is: a sprite is a drawing, and a
 * drawing that cannot be re-drawn is a drawing nobody will ever fix. Run it
 * after changing a row:
 *
 *   node guardian/generate.mjs
 *
 * It writes the four vector sprites into the app's drawable folder and a preview
 * PNG beside this file, so the shapes can be looked at without a device.
 *
 * Palette, all of it ALIVE's own tokens - the Guardian dresses itself out of the
 * same cupboard as everything else and adds no colour to the app:
 *
 *   #  charcoal body    @color/alive_ink             the same ink every edge is made of
 *   D  deep crimson     @color/alive_red_short_dark  the role colour for time running out
 *   C  crimson          @color/alive_red_short_mid   its lit twin
 *   A  amber eye        @color/alive_amber_action    the one colour ALIVE means as "look here"
 *   M  dark metal       @color/alive_warm_gray       a surface, never a sentence
 */
import { writeFileSync } from 'node:fs';
import { deflateSync } from 'node:zlib';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const drawable = join(here, '../app/src/main/res/drawable');

const TOKEN = {
  '#': { colour: '@color/alive_ink', hex: '#1D1D1B' },
  D: { colour: '@color/alive_red_short_dark', hex: '#9E2F22' },
  C: { colour: '@color/alive_red_short_mid', hex: '#D9553F' },
  A: { colour: '@color/alive_amber_action', hex: '#F7C948' },
  M: { colour: '@color/alive_warm_gray', hex: '#D8D2C7' },
  P: { colour: '@color/alive_paper', hex: '#F5F1E8' },
};

/**
 * Idle: standing watch. Three-pronged horns, and a mouth you can see into -
 * the sprite is the same character as the poster now, at a sixteenth of the
 * size: the app's guardian stopped being a toy the day it got a legend.
 */
const IDLE = [
  '..C..........C..',
  '..##........##..',
  '..##........##..',
  '...##......##...',
  '....########....',
  '...##########...',
  '...#AA####AA#...',
  '...#DDDDDDDD#...',
  '...#P#P##P#P#...',
  '...#P#P##P#P#...',
  '....########....',
  '..C##########C..',
  '....########....',
  '....########....',
  '....##....##....',
  '...MMM....MMM...',
];

/**
 * Alert: it has noticed. The eyes open into bars, the lip lights up, and two
 * sparks break over the horns. Same body, unmistakably a different face.
 */
const ALERT = [
  '..CC........CC..',
  '..##........##..',
  '..##........##..',
  '...##......##...',
  '....########....',
  '...##########...',
  '...#AAA##AAA#...',
  '...#CCCCCCCC#...',
  '...#P#P##P#P#...',
  '...#P#P##P#P#...',
  '....########....',
  '..C##########C..',
  '....########....',
  '....########....',
  '....##....##....',
  '...MMM....MMM...',
];

/**
 * Recovery: the mouth shut, and a shield held out at its side. A guardian
 * offering a way back does not grin - so this is the one pose with no fangs.
 */
const RECOVERY = [
  '..C..........C..',
  '..##........##..',
  '..##........##..',
  '...##......##...',
  '....########....',
  '...##########...',
  '...#AA####AA#...',
  '...#DDDDDDDD#...',
  '...##########...',
  '...##########...',
  '....########....',
  '..C##########CMM',
  '....########MMM.',
  '....##....##.CC.',
  '....##....##....',
  '...MMM....MMM...',
];

/**
 * Restored: on watch again with the core burning and the eyes narrowed back to
 * slits, because a watch that is back on is not a watch that is excited.
 */
const RESTORED = [
  '..C..........C..',
  '..##........##..',
  '..##........##..',
  '...##......##...',
  '....########....',
  '...##########...',
  '...#A######A#...',
  '...#DDDDDDDD#...',
  '...#P#P##P#P#...',
  '...#P#P##P#P#...',
  '....########....',
  '..C##########C..',
  '....###CC###....',
  '....########....',
  '....##....##....',
  '...MMM....MMM...',
];

const POSES = { idle: IDLE, alert: ALERT, recovery: RECOVERY, restored: RESTORED };

// A row that is a character short is a sprite with a bite taken out of it, and
// it fails silently: the vector just draws less. Refuse to write one.
for (const [name, rows] of Object.entries(POSES)) {
  rows.forEach((row, y) => {
    if (row.length !== 16) {
      throw new Error(`${name} row ${y} is ${row.length} cells, not 16: "${row}"`);
    }
  });
}

/** One `<path>` per colour: every maximal horizontal run becomes a rectangle. */
function pathsOf(rows) {
  const byColour = new Map();
  rows.forEach((row, y) => {
    let x = 0;
    while (x < row.length) {
      const symbol = row[x];
      if (symbol === '.') {
        x += 1;
        continue;
      }
      let width = 1;
      while (x + width < row.length && row[x + width] === symbol) width += 1;

      const run = `M${x},${y}h${width}v1h-${width}z`;
      byColour.set(symbol, (byColour.get(symbol) ?? '') + run);
      x += width;
    }
  });
  return [...byColour.entries()];
}

const NAMES = {
  idle: 'Guardian, standing watch',
  alert: 'Guardian, alert',
  recovery: 'Guardian, offering a way back',
  restored: 'Guardian, watch restored',
};

for (const [name, rows] of Object.entries(POSES)) {
  const paths = pathsOf(rows)
    .map(
      ([symbol, data]) =>
        `    <path\n        android:fillColor="${TOKEN[symbol].colour}"\n        android:pathData="${data}" />`
    )
    .join('\n');

  const xml = `<?xml version="1.0" encoding="utf-8"?>
<!-- ALIVE Guardian, pose: ${name}. ${NAMES[name]}.
     Drawn on the same 16x16 grid as the critter and the pixel marks; regenerate
     with guardian/generate.mjs rather than editing the paths by hand. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="48dp"
    android:height="48dp"
    android:viewportWidth="16"
    android:viewportHeight="16">
${paths}
</vector>
`;
  writeFileSync(join(drawable, `ic_guardian_${name}.xml`), xml);
}

// ---------- PREVIEW ----------
// Only so a human can look at the shapes without installing anything: four
// poses on the page ground, four on the card face they sit on in the app.

const CELL = 12;
const GAP = 6;
const POSES_PER_ROW = 4;
const ROWS = [
  { ground: '#F5F1E8', label: 'paper' },
  { ground: '#FFFFFF', label: 'card' },
];
const W = POSES_PER_ROW * (16 * CELL + GAP) + GAP;
const H = ROWS.length * (16 * CELL + GAP) + GAP;

const pixels = Array.from({ length: H }, () => Array(W).fill(null));
ROWS.forEach((row, rowIndex) => {
  const top = GAP + rowIndex * (16 * CELL + GAP);
  Object.values(POSES).forEach((rows, poseIndex) => {
    const left = GAP + poseIndex * (16 * CELL + GAP);
    rows.forEach((line, y) => {
      [...line].forEach((symbol, x) => {
        if (symbol === '.') return;
        for (let dy = 0; dy < CELL; dy++) {
          for (let dx = 0; dx < CELL; dx++) {
            pixels[top + y * CELL + dy][left + x * CELL + dx] = TOKEN[symbol].hex;
          }
        }
      });
    });
  });
});

const rgb = (hex) => [
  parseInt(hex.slice(1, 3), 16),
  parseInt(hex.slice(3, 5), 16),
  parseInt(hex.slice(5, 7), 16),
];

const groundFor = (y) => {
  const rowIndex = Math.floor((y - GAP) / (16 * CELL + GAP));
  const ground = ROWS[rowIndex]?.ground ?? '#F5F1E8';
  return rgb(ground);
};

const crcTable = Array.from({ length: 256 }, (_, n) => {
  let c = n;
  for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
  return c >>> 0;
});

const chunk = (type, body) => {
  const len = Buffer.alloc(4);
  len.writeUInt32BE(body.length);
  const head = Buffer.concat([Buffer.from(type, 'ascii'), body]);
  let c = 0xffffffff;
  for (const byte of head) c = crcTable[(c ^ byte) & 0xff] ^ (c >>> 8);
  const tail = Buffer.alloc(4);
  tail.writeUInt32BE((c ^ 0xffffffff) >>> 0);
  return Buffer.concat([len, head, tail]);
};

const stride = W * 3;
const raw = Buffer.alloc((stride + 1) * H);
for (let y = 0; y < H; y++) {
  raw[y * (stride + 1)] = 0;
  for (let x = 0; x < W; x++) {
    const [r, g, b] = pixels[y][x] ? rgb(pixels[y][x]) : groundFor(y);
    const o = y * (stride + 1) + 1 + x * 3;
    raw[o] = r;
    raw[o + 1] = g;
    raw[o + 2] = b;
  }
}

const ihdr = Buffer.alloc(13);
ihdr.writeUInt32BE(W, 0);
ihdr.writeUInt32BE(H, 4);
ihdr[8] = 8;
ihdr[9] = 2;

const out = join(here, 'preview.png');
writeFileSync(
  out,
  Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', ihdr),
    chunk('IDAT', deflateSync(raw, { level: 9 })),
    chunk('IEND', Buffer.alloc(0)),
  ])
);

console.log(`wrote 4 sprites to res/drawable and ${out} (${W}x${H}, ${Object.keys(POSES).join(', ')})`);
