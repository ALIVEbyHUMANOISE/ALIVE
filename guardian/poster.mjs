/**
 * The Guardian, at the size of a poster.
 *
 * The sprite that stands on the Home card is 16x16 and says "here, watching".
 * This is the same character at 48x48, drawn for the one section of the site
 * where the argument is not "here is a number" but "this is what is standing
 * between you and the thing you are avoiding". Same palette as the sprite - the
 * app's own tokens, no new colour - with the metal dropped and the crimson
 * pushed, because a poster has room for fear that a 16px face does not.
 *
 *   node guardian/poster.mjs
 *
 * Writes the PNG the site serves, plus a copy beside this file for review.
 */
import { writeFileSync } from 'node:fs';
import { deflateSync } from 'node:zlib';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const site = join(here, '../web/public/images/guardian.png');

const PALETTE = {
  '#': '#1D1D1B', // charcoal body
  D: '#9E2F22', // deep crimson
  C: '#D9553F', // lit crimson
  A: '#F7C948', // amber: the eyes, and only the eyes
  P: '#F5F1E8', // paper: teeth and the light behind them
  M: '#D8D2C7', // metal
};

const W = 48;
const H = 48;
const grid = Array.from({ length: H }, () => Array(W).fill('.'));

const set = (x, y, colour) => {
  if (x >= 0 && x < W && y >= 0 && y < H) grid[y][x] = colour;
};

/** The same shape on both sides of the centre line: a guardian is symmetrical. */
const both = (x) => [x, W - 1 - x];

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
      if ((y >= Math.min(y1, y2)) && (y < Math.max(y1, y2))) {
        crossings.push(x1 + ((y - y1) * (x2 - x1)) / (y2 - y1));
      }
    }
    crossings.sort((a, b) => a - b);
    for (let i = 0; i + 1 < crossings.length; i += 2) {
      for (let x = Math.round(crossings[i]); x <= Math.round(crossings[i + 1]); x++) {
        set(x, y, colour);
      }
    }
  }
};

/** A shape and its mirror image, described once. */
const polyBoth = (points, colour) => {
  poly(points, colour);
  poly(points.map(([x, y]) => [W - 1 - x, y]), colour);
};

// ---------- HORNS ----------
// Crooked, thick at the base, tapering past the frame: the silhouette does the
// scaring before any detail is read.
polyBoth([[12, 15], [9, 5], [6, 1], [11, 1], [15, 6], [17, 15]], '#');
polyBoth([[9, 9], [7, 3], [8, 2], [12, 8], [13, 14], [10, 14]], 'D');

// ---------- HEAD ----------
// A broad skull: flat top, tapering jaw, corners cut so it reads as a mask.
polyBoth([[15, 12], [32, 12], [35, 15], [35, 27], [31, 36], [16, 36], [12, 27], [12, 15]], '#');
// One thin brow. It was three rows thick once and the face read as a stack of
// bands rather than as a face.
rect(13, 17, 34, 18, 'D');

// ---------- EYES ----------
// Two narrow slits with four cells of skull between them. Crooked, and the
// brightest thing in the picture by a wide margin - which is the whole reason
// the amber is reserved for eyes everywhere else in the app.
polyBoth([[14, 20], [21, 21], [21, 23], [15, 23]], 'A');
polyBoth([[15, 23], [21, 23], [21, 24], [15, 24]], 'C');
polyBoth([[13, 19], [16, 19], [16, 20], [13, 20]], 'C');

// ---------- MAW ----------
// Pointed teeth hanging into a dark mouth. Rectangle teeth merged into one pale
// block at this size, and the pale block then out-shouted the eyes - the one
// thing in the picture that is allowed to be the brightest.
rect(15, 26, 32, 26, 'C');
rect(15, 27, 32, 33, 'D');
[16, 20, 24, 28].forEach((x) => poly([[x, 27], [x + 3, 27], [x + 1, 32]], 'P'));
// The lower lip, so the mouth ends on a line instead of fading into the chin.
rect(15, 34, 32, 35, '#');
polyBoth([[15, 34], [16, 34], [16, 35], [15, 35]], 'C');

// ---------- CHEST AND CLAWS ----------
// The bottom of the frame is his: shoulders that run off both edges, claws
// hooked over the paper.
polyBoth([[6, 48], [10, 37], [15, 35], [33, 35], [38, 37], [42, 48]], '#');
polyBoth([[9, 41], [14, 41], [14, 43], [9, 43]], 'D');
polyBoth([[7, 48], [9, 43], [11, 46], [13, 43], [14, 48]], 'M');
polyBoth([[8, 48], [9, 45], [10, 48]], 'C');

// ---------- RIM ----------
// One line of light along the top of each shoulder: the only thing that says
// the body has a shape rather than being a hole in the page.
polyBoth([[11, 37], [15, 36], [15, 38], [12, 39]], 'C');

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

const png = () => {
  const stride = W * CELL * 3;
  const raw = Buffer.alloc((stride + 1) * H * CELL);
  const cache = new Map();
  for (let y = 0; y < H * CELL; y++) {
    raw[y * (stride + 1)] = 0;
    const gy = Math.floor(y / CELL);
    for (let x = 0; x < W * CELL; x++) {
      const symbol = grid[gy][Math.floor(x / CELL)];
      const hex = PALETTE[symbol] ?? PALETTE['.'];
      const ground = symbol === '.' ? '#F5F1E8' : hex;
      let rgbValues = cache.get(ground);
      if (!rgbValues) {
        rgbValues = rgb(ground);
        cache.set(ground, rgbValues);
      }
      const o = y * (stride + 1) + 1 + x * 3;
      raw[o] = rgbValues[0];
      raw[o + 1] = rgbValues[1];
      raw[o + 2] = rgbValues[2];
    }
  }

  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(W * CELL, 0);
  ihdr.writeUInt32BE(H * CELL, 4);
  ihdr[8] = 8;
  ihdr[9] = 2;

  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', ihdr),
    chunk('IDAT', deflateSync(raw, { level: 9 })),
    chunk('IEND', Buffer.alloc(0)),
  ]);
};

const bytes = png();
writeFileSync(site, bytes);
writeFileSync(join(here, 'poster.png'), bytes);
console.log(`wrote ${site} and guardian/poster.png (${W * CELL}x${H * CELL})`);
