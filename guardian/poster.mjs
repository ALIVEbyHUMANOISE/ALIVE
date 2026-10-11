/**
 * The Guardian, at the size of a poster - and at the size of a myth.
 *
 * Two things make this different from the sprite that stands on the Home card:
 * the figure is cropped close (a face filling the frame is a face you cannot
 * look away from), and every edge of it is rimmed in deep crimson, so the
 * silhouette reads even on the ink ground the site mounts it on. The sprite
 * disappears into that ground; this one burns out of it.
 *
 * Drawn on a 48x48 grid from the same five tokens as everything else, with a
 * transparent ground - no sixth colour, and no gradient anywhere, because the
 * whole language the app speaks is flat shapes with hard edges.
 *
 *   node guardian/poster.mjs
 */
import { writeFileSync } from 'node:fs';
import { deflateSync } from 'node:zlib';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const site = join(here, '../web/public/images/guardian.png');

const PALETTE = {
  '#': '#1D1D1B', // charcoal body
  D: '#9E2F22', // deep crimson: the rim, the sockets, the shadow
  C: '#D9553F', // lit crimson: cracks and the inner lip
  A: '#F7C948', // amber: the eyes, and only the eyes
  P: '#F5F1E8', // paper: teeth
};

const W = 48;
const H = 48;
const grid = Array.from({ length: H }, () => Array(W).fill('.'));

const set = (x, y, colour) => {
  if (x >= 0 && x < W && y >= 0 && y < H) grid[y][x] = colour;
};

const rect = (x0, y0, x1, y1, colour) => {
  for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) set(x, y, colour);
};

/** Polygon fill by scanlines, so a horn can be a horn rather than a stack of rows. */
const poly = (points, colour) => {
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
      for (let x = Math.round(crossings[i]); x <= Math.round(crossings[i + 1]); x++) set(x, y, colour);
    }
  }
};

/** Every shape here is drawn once and mirrored: a guardian is symmetrical. */
const polyBoth = (points, colour) => {
  poly(points, colour);
  poly(points.map(([x, y]) => [W - 1 - x, y]), colour);
};

// ---------- HORNS ----------
// Three prongs a side, sweeping past the frame. The first draft was two smooth
// horns and read as a costume; jagged reads as a thing. The charcoal carries the
// mass and the crimson is a line inside it, because a horn that is mostly red
// stops being a horn.
polyBoth([[13, 18], [11, 8], [5, 0], [9, 0], [13, 6], [16, 12]], '#');
polyBoth([[15, 14], [17, 4], [14, 0], [19, 0], [21, 9], [19, 15]], '#');
polyBoth([[19, 12], [24, 2], [23, 0], [28, 0], [26, 11], [23, 14]], '#');
polyBoth([[14, 16], [13, 9], [15, 8], [17, 13], [17, 17], [15, 17]], 'D');

// ---------- HEAD ----------
// Filling the frame: twelve cells of forehead above the brow, a jaw that
// narrows to nothing, corners cut so it reads as a mask rather than a ball.
polyBoth([[11, 17], [17, 14], [31, 14], [37, 17], [37, 32], [33, 41], [15, 41], [11, 32]], '#');

// ---------- BROW AND SOCKETS ----------
// A thin line above each eye instead of one band across the face. The band made
// a wide red bar with eyes underneath it; the line makes a black face with two
// things burning in it, which is the whole trick.
polyBoth([[13, 22], [22, 23], [22, 24], [13, 23]], 'D');
polyBoth([[12, 20], [14, 18], [16, 22], [13, 24]], 'D');

// ---------- EYES ----------
// Long, narrow, and tapering at the inner end: they meet nothing in the middle,
// so the stare is aimed rather than blank.
polyBoth([[14, 25], [22, 25], [22, 27], [14, 27]], 'A');
polyBoth([[13, 24], [15, 24], [15, 26], [13, 26]], 'C');

// ---------- MAW ----------
// A black cavity with fangs hanging into it and one tusk at each corner. The
// interior stays ink: a mouth lit up in red is a carved pumpkin, a mouth you
// cannot see into is worse.
rect(12, 33, 35, 40, '#');
rect(12, 33, 35, 33, 'D');
[17, 21, 25, 29].forEach((x) => poly([[x, 34], [x + 2, 34], [x + 1, 37]], 'P'));
polyBoth([[13, 34], [16, 34], [15, 40]], 'P');

// ---------- CHEST AND CLAWS ----------
// Shoulders that run off both edges, claws hooked over the bottom of the frame.
polyBoth([[2, 48], [7, 42], [13, 41], [35, 41], [41, 42], [46, 48]], '#');
polyBoth([[6, 46], [12, 46], [12, 48], [6, 48]], 'D');
polyBoth([[3, 48], [6, 45], [8, 48]], 'C');

// ---------- SCARS ----------
// One thin line down each cheek: something the Guardian has already survived.
polyBoth([[12, 30], [13, 30], [13, 33], [12, 33]], 'C');

// ---------- RIM ----------
// One cell of deep crimson around the whole silhouette. On the site's ink
// ground this is the difference between a shape and a hole in the page.
const filled = (x, y) => x >= 0 && x < W && y >= 0 && y < H && grid[y][x] !== '.';
const silhouette = [];
for (let y = 0; y < H; y++) {
  for (let x = 0; x < W; x++) if (filled(x, y)) silhouette.push([x, y]);
}
silhouette.forEach(([x, y]) => {
  [
    [x + 1, y],
    [x - 1, y],
    [x, y + 1],
    [x, y - 1],
  ].forEach(([nx, ny]) => {
    if (!filled(nx, ny) && nx >= 0 && nx < W && ny >= 0 && ny < H) set(nx, ny, 'D');
  });
});

// ---------- PNG ----------
const CELL = 16;
const rgb = (hex) => [
  parseInt(hex.slice(1, 3), 16),
  parseInt(hex.slice(3, 5), 16),
  parseInt(hex.slice(5, 7), 16),
];

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

/** Truecolour, opaque: the figure is a lit plate, and the plate is its own ground. */
const png = (ground) => {
  const stride = W * CELL * 3;
  const raw = Buffer.alloc((stride + 1) * H * CELL);
  const cache = new Map();
  for (let y = 0; y < H * CELL; y++) {
    raw[y * (stride + 1)] = 0;
    const gy = Math.floor(y / CELL);
    for (let x = 0; x < W * CELL; x++) {
      const symbol = grid[gy][Math.floor(x / CELL)];
      const hex = symbol === '.' ? ground : PALETTE[symbol];
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
  ihdr.writeUInt32BE(W * CELL, 0);
  ihdr.writeUInt32BE(H * CELL, 4);
  ihdr[8] = 8;
  ihdr[9] = 2; // truecolour

  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', ihdr),
    chunk('IDAT', deflateSync(raw, { level: 9 })),
    chunk('IEND', Buffer.alloc(0)),
  ]);
};

writeFileSync(site, png('#F5F1E8'));
// A review copy on the ink ground, so the plate can be judged on the wall it
// hangs on rather than on white.
writeFileSync(join(here, 'poster.png'), png('#1D1D1B'));
console.log(`wrote ${site} and guardian/poster.png (on ink)`);
