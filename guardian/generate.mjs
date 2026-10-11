/**
 * The Guardian — one figure, four poses, drawn at 48x48.
 *
 * The 16x16 sheet this replaces was a mask with horns on it: no body, no
 * equipment, no light. It read as a badge, not as something standing in a
 * doorway, and the reference art is unanimous about what actually makes a
 * guardian read at a glance - armour with real mass, a weapon, and a core that
 * glows. All three are here, and all three are drawn from the app's own tokens:
 * charcoal, crimson, amber and the warm grey the app already calls metal. No new
 * colour, no gradient, hard edges - the same language as every other sprite.
 *
 * The four poses differ only in the face and the light, because a guardian
 * turning its head is a guardian deciding; a guardian redrawn from scratch is a
 * different character.
 *
 *   node guardian/generate.mjs
 *
 * Writes the four app vectors, a preview sheet for review, and the PNG the site
 * serves - all from this one drawing, so the app and the legend cannot drift.
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

const W = 48;
const H = 48;

/** One drawing per pose, painted the same way and then lit differently. */
const POSES = ['idle', 'alert', 'recovery', 'restored'];

function blank() {
  return Array.from({ length: H }, () => Array(W).fill('.'));
}

const paint = {
  set(grid, x, y, colour) {
    if (x >= 0 && x < W && y >= 0 && y < H) grid[y][x] = colour;
  },
  rect(grid, x0, y0, x1, y1, colour) {
    for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) paint.set(grid, x, y, colour);
  },
  poly(grid, points, colour) {
    const ys = points.map((p) => p[1]);
    for (let y = Math.min(...ys); y <= Math.max(...ys); y++) {
      const crossings = [];
      for (let i = 0; i < points.length; i++) {
        const [x1, y1] = points[i];
        const [x2, y2] = points[(i + 1) % points.length];
        if (y1 === y2) continue;
        if (y >= Math.min(y1, y2) && y < Math.max(y1, y2)) {
          crossings.push(x1 + ((y - y1) * (x2 - x1)) / (y2 - y1));
        }
      }
      crossings.sort((a, b) => a - b);
      for (let i = 0; i + 1 < crossings.length; i += 2) {
        for (let x = Math.round(crossings[i]); x <= Math.round(crossings[i + 1]); x++) paint.set(grid, x, y, colour);
      }
    }
  },
  polyBoth(grid, points, colour) {
    paint.poly(grid, points, colour);
    paint.poly(grid, points.map(([x, y]) => [W - 1 - x, y]), colour);
  },
};

/** One cell of deep crimson around everything, so the figure exists on ink. */
function rim(grid) {
  const filled = (x, y) => x >= 0 && x < W && y >= 0 && y < H && grid[y][x] !== '.';
  const edge = [];
  for (let y = 0; y < H; y++) {
    for (let x = 0; x < W; x++) {
      if (filled(x, y)) continue;
      if (filled(x + 1, y) || filled(x - 1, y) || filled(x, y + 1) || filled(x, y - 1)) edge.push([x, y]);
    }
  }
  edge.forEach(([x, y]) => paint.set(grid, x, y, 'D'));
}

/**
 * The figure. Everything below is one body; [pose] only changes the face and
 * the light, and [lit] decides how hard the core burns.
 *
 * It is a cloaked shape, not a suit of armour, and that is a decision made twice.
 * The first two passes built a figure out of plates - pauldrons, chest piece,
 * arm bands - and at forty-eight pixels with four colours it came out reading as
 * a toy robot: every shape the same weight, no silhouette to be afraid of. A
 * cloak is one big dark mass with a lit face and a lit weapon, which is what the
 * eye actually reads as "guardian" from across a room.
 */
function draw(pose) {
  const grid = blank();
  const lit = pose === 'alert' || pose === 'restored';
  const glow = pose === 'alert' ? 'C' : 'D';

  // ---------- CLOAK ----------
  // Shoulders narrow, hem wide: the whole body is this one shape, and the figure
  // stands on the hem rather than on legs.
  paint.polyBoth(grid, [[17, 14], [30, 14], [35, 22], [40, 40], [42, 46], [5, 46], [7, 40], [12, 22]], '#');
  paint.rect(grid, 5, 44, 42, 46, '#');
  // Two feet under the hem, so it is standing rather than floating.
  paint.polyBoth(grid, [[17, 46], [22, 46], [22, 47], [16, 47]], 'D');

  // ---------- HOOD ----------
  paint.polyBoth(grid, [[18, 4], [29, 4], [35, 10], [36, 18], [11, 18], [12, 10]], '#');
  paint.polyBoth(grid, [[11, 16], [18, 16], [18, 19], [11, 18]], '#');;

  // ---------- HORNS ----------
  // Small, swept back and ink only. The crimson on them is the rim, not paint:
  // a lit tip sticking out of the silhouette turned into a red blob hanging off
  // the hood, and the test that caught it is the reason this rule is written
  // down here.
  paint.polyBoth(grid, [[18, 5], [13, 1], [10, 0], [14, 4], [17, 7]], '#');
  paint.polyBoth(grid, [[29, 5], [34, 1], [37, 0], [33, 4], [30, 7]], '#');

  // ---------- THE FACE IN THE HOOD ----------
  // Two slits of amber with a cell of dark between them, and nothing else. This
  // is the whole reason the cloak works: the only thing lit is the part looking
  // at you - and they have to stay *two*, because a mirrored pair with no gap
  // merges into one visor and the face stops being a face.
  const eye = pose === 'alert' ? [[19, 9], [22, 9], [22, 12], [18, 12]]
    : pose === 'recovery' ? [[19, 10], [22, 10], [22, 12], [19, 12]]
      : pose === 'restored' ? [[20, 10], [21, 10], [21, 12], [20, 12]]
        : [[19, 10], [22, 10], [22, 12], [19, 12]];
  paint.polyBoth(grid, eye, 'A');
  paint.polyBoth(grid, [[18, 12], [22, 12], [22, 13], [18, 13]], 'D');

  // ---------- SIGIL ----------
  // The mark on the cloak, where a chest would be: a crimson ring around a core
  // that burns harder in the two poses that are doing something about it.
  paint.rect(grid, 20, 24, 27, 31, 'D');
  paint.polyBoth(grid, [[19, 26], [20, 24], [21, 26], [19, 28]], 'D');
  paint.rect(grid, 21, 25, 26, 30, glow);
  paint.rect(grid, 22, 26, 25, 29, lit ? 'A' : 'C');
  paint.rect(grid, 23, 27, 24, 28, '#');

  // ---------- STAFF ----------
  // Held out to the side, thin shaft, and a head that is the brightest thing in
  // the drawing after the eyes. Drawn once and NOT mirrored: the shared `Both`
  // helpers are for the body, and mirroring the weapon put a second glowing head
  // on the empty side of the canvas - which is how this file learned that the
  // helper is the thing to check when something appears that nobody drew.
  paint.rect(grid, 41, 6, 42, 46, 'M');
  paint.rect(grid, 41, 6, 41, 46, '#');
  paint.rect(grid, 42, 6, 42, 46, '#');
  paint.rect(grid, 40, 30, 43, 33, '#');
  paint.poly(grid, [[41, 0], [45, 4], [41, 8], [37, 4]], 'C');
  paint.rect(grid, 40, 3, 43, 5, 'D');
  paint.rect(grid, 41, 3, 42, 5, 'A');

  // ---------- LIGHTS ----------
  if (pose === 'alert') {
    // Sparks off the horns: the only pose that is shouting.
    paint.polyBoth(grid, [[12, 0], [15, 2], [12, 4], [9, 2]], 'C');
  }
  if (pose === 'recovery') {
    // A shield raised across the body, held rather than worn - the one pose that
    // offers something instead of standing in the way.
    paint.polyBoth(grid, [[4, 26], [15, 24], [16, 42], [4, 44]], 'M');
    paint.rect(grid, 4, 26, 15, 28, 'D');
    paint.rect(grid, 9, 33, 11, 35, 'C');
  }

  rim(grid);
  return grid;
}

/** Row strings, for the paths and for the review sheet. */
const rowsOf = (grid) => grid.map((row) => row.join(''));

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

const drawing = Object.fromEntries(POSES.map((pose) => [pose, rowsOf(draw(pose))]));

// A text dump, because "which cell is not touching what" is a question for the
// eye and not for an assertion message.
if (process.argv.includes('--dump')) {
  POSES.forEach((pose) => {
    console.log(`\n${pose}`);
    drawing[pose].forEach((row, y) => console.log(String(y).padStart(2, ' ') + ' ' + row));
  });
}

for (const [pose, rows] of Object.entries(drawing)) {
  rows.forEach((row, y) => {
    if (row.length !== W) throw new Error(`${pose} row ${y} is ${row.length} cells, not ${W}`);
  });

  const paths = pathsOf(rows)
    .map(
      ([symbol, data]) =>
        `    <path\n        android:fillColor="${TOKEN[symbol].colour}"\n        android:pathData="${data}" />`
    )
    .join('\n');

  const xml = `<?xml version="1.0" encoding="utf-8"?>
<!-- ALIVE Guardian, pose: ${pose}. One figure in four lights - the body, the
     staff and the armour are identical in all four; only the face and the core
     change. Regenerate with guardian/generate.mjs rather than editing paths. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="96dp"
    android:height="96dp"
    android:viewportWidth="${W}"
    android:viewportHeight="${H}">
${paths}
</vector>
`;
  writeFileSync(join(drawable, `ic_guardian_${pose}.xml`), xml);
}

// ---------- PNG ----------
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

const rgb = (hex) => [
  parseInt(hex.slice(1, 3), 16),
  parseInt(hex.slice(3, 5), 16),
  parseInt(hex.slice(5, 7), 16),
];

/** [sheet] paints all four side by side; the singles are the site and the app. */
function png(pixelAt, width, height) {
  const stride = width * 3;
  const raw = Buffer.alloc((stride + 1) * height);
  const cache = new Map();
  for (let y = 0; y < height; y++) {
    raw[y * (stride + 1)] = 0;
    for (let x = 0; x < width; x++) {
      const hex = pixelAt(x, y);
      let values = cache.get(hex);
      if (!values) {
        values = rgb(hex);
        cache.set(hex, values);
      }
      const o = y * (stride + 1) + 1 + x * 3;
      raw[o] = values[0];
      raw[o + 1] = values[1];
      raw[o + 2] = values[2];
    }
  }

  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(width, 0);
  ihdr.writeUInt32BE(height, 4);
  ihdr[8] = 8;
  ihdr[9] = 2;

  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', ihdr),
    chunk('IDAT', deflateSync(raw, { level: 9 })),
    chunk('IEND', Buffer.alloc(0)),
  ]);
}

const INK = '#1D1D1B';
const PAPER = '#F5F1E8';
const CELL = 8;
const GAP = 8;
const sheetW = POSES.length * (W * CELL + GAP) + GAP;
const sheetH = 2 * (H * CELL + GAP) + GAP;

writeFileSync(
  join(here, 'preview.png'),
  png(
    (x, y) => {
      const row = Math.floor((y - GAP) / (H * CELL + GAP));
      const ground = row === 0 ? PAPER : INK;
      if (row < 0 || row > 1) return ground;
      const top = GAP + row * (H * CELL + GAP);
      if (y < top || y >= top + H * CELL) return ground;
      const col = Math.floor((x - GAP) / (W * CELL + GAP));
      if (col < 0 || col >= POSES.length) return ground;
      const left = GAP + col * (W * CELL + GAP);
      if (x < left || x >= left + W * CELL) return ground;
      const cell = drawing[POSES[col]][Math.floor((y - top) / CELL)][Math.floor((x - left) / CELL)];
      return cell === '.' ? ground : TOKEN[cell].hex;
    },
    sheetW,
    sheetH
  )
);

// The site's hero: the alert pose on its own, at 16 screen pixels a cell.
const HERO_CELL = 16;
writeFileSync(
  join(here, '../web/public/images/guardian.png'),
  png(
    (x, y) => {
      const cell = drawing.alert[Math.floor(y / HERO_CELL)][Math.floor(x / HERO_CELL)];
      return cell === '.' ? PAPER : TOKEN[cell].hex;
    },
    W * HERO_CELL,
    H * HERO_CELL
  )
);

console.log(`wrote 4 vectors at ${W}x${H}, guardian/preview.png and the site hero`);
