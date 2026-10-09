// ALIVE poster: "your phone has taken over your life".
//
// The sketch it comes from is a phone with a monster face, two claws closing on
// a small figure, and that figure is now ALIVE's own critter. Everything here is
// drawn on one grid and written out as a PNG with nearest-neighbour scaling, so
// the result is real pixel art rather than a picture of pixel art - and so the
// composition can be re-rendered after any tweak instead of redrawn.
//
//   node poster/generate.mjs          writes alive-taken-over.png, prints a preview
//   node poster/generate.mjs --quiet  only writes the file

import { writeFileSync } from 'node:fs';
import { deflateSync } from 'node:zlib';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const W = 96;
const H = 120;
const CELL = 12; // 1152 x 1440, a 4:5 poster

// The app's own palette, by role: paper is the canvas, ink is everything that
// is not the critter, pink is the critter alone, and gold is the only warmth.
const PAL = {
  '.': '#F5F1E8', // alive_paper
  '#': '#1D1D1B', // alive_ink
  'w': '#FFFDF6', // the screen, lit
  'd': '#DCD5C4', // a shade of paper, for the cast shadow
  'f': '#315C45', // alive_forest
  's': '#E58CA4', // alive_pink_soft - the critter, and nothing else
};

const grid = Array.from({ length: H }, () => Array(W).fill('.'));

const set = (x, y, c) => {
  const xi = Math.round(x);
  const yi = Math.round(y);
  if (xi >= 0 && yi >= 0 && xi < W && yi < H) grid[yi][xi] = c;
};

/** Is (i, j) inside a rectangle whose corners are rounded by r cells. */
const inRounded = (i, j, w, h, r) => {
  const cx = Math.min(Math.max(i, r), w - 1 - r);
  const cy = Math.min(Math.max(j, r), h - 1 - r);
  const dx = i - cx;
  const dy = j - cy;
  return dx * dx + dy * dy <= r * r;
};

const rect = (x, y, w, h, c) => {
  for (let j = 0; j < h; j++) for (let i = 0; i < w; i++) set(x + i, y + j, c);
};

const roundedRect = (x, y, w, h, r, c) => {
  for (let j = 0; j < h; j++) for (let i = 0; i < w; i++) if (inRounded(i, j, w, h, r)) set(x + i, y + j, c);
};

/** A rounded card drawn as an edge of t cells, the app's own frame. */
const frame = (x, y, w, h, t, r, c) => {
  for (let j = 0; j < h; j++) {
    for (let i = 0; i < w; i++) {
      if (!inRounded(i, j, w, h, r)) continue;
      if (i < t || i >= w - t || j < t || j >= h - t) set(x + i, y + j, c);
    }
  }
};

const oval = (cx, cy, rx, ry, c) => {
  for (let j = -ry; j <= ry; j++) {
    for (let i = -rx; i <= rx; i++) {
      if ((i * i) / (rx * rx) + (j * j) / (ry * ry) <= 1.05) set(cx + i, cy + j, c);
    }
  }
};

/** A filled polygon on the grid - the only way to get a solid curved shape. */
const polygon = (pts, c) => {
  const ys = pts.map((p) => p[1]);
  for (let y = Math.floor(Math.min(...ys)); y <= Math.ceil(Math.max(...ys)); y++) {
    const xs = [];
    for (let i = 0; i < pts.length; i++) {
      const [x0, y0] = pts[i];
      const [x1, y1] = pts[(i + 1) % pts.length];
      if ((y0 <= y && y1 > y) || (y1 <= y && y0 > y)) xs.push(x0 + ((y - y0) * (x1 - x0)) / (y1 - y0));
    }
    xs.sort((a, b) => a - b);
    for (let k = 0; k + 1 < xs.length; k += 2) {
      for (let x = Math.round(xs[k]); x <= Math.round(xs[k + 1]); x++) set(x, y, c);
    }
  }
};

/** A spine with a half-width at every point, filled: a solid tapered limb. */
const ribbon = (spine, halfWidths, c) => {
  const left = [];
  const right = [];
  spine.forEach(([x, y], i) => {
    const [px, py] = spine[Math.max(0, i - 1)];
    const [qx, qy] = spine[Math.min(spine.length - 1, i + 1)];
    const len = Math.hypot(qx - px, qy - py) || 1;
    const [nx, ny] = [-(qy - py) / len, (qx - px) / len];
    left.push([x + nx * halfWidths[i], y + ny * halfWidths[i]]);
    right.push([x - nx * halfWidths[i], y - ny * halfWidths[i]]);
  });
  polygon([...left, ...right.reverse()], c);
};

// ---------- 5x7 pixel lettering, capitals only ----------

const FONT = {
  A: ['.###.', '#...#', '#...#', '#####', '#...#', '#...#', '#...#'],
  E: ['#####', '#....', '#....', '####.', '#....', '#....', '#####'],
  F: ['#####', '#....', '#....', '####.', '#....', '#....', '#....'],
  H: ['#...#', '#...#', '#...#', '#####', '#...#', '#...#', '#...#'],
  I: ['#####', '..#..', '..#..', '..#..', '..#..', '..#..', '#####'],
  K: ['#...#', '#..#.', '#.#..', '##...', '#.#..', '#..#.', '#...#'],
  L: ['#....', '#....', '#....', '#....', '#....', '#....', '#####'],
  N: ['#...#', '##..#', '#.#.#', '#..##', '#...#', '#...#', '#...#'],
  O: ['.###.', '#...#', '#...#', '#...#', '#...#', '#...#', '.###.'],
  P: ['####.', '#...#', '#...#', '####.', '#....', '#....', '#....'],
  R: ['####.', '#...#', '#...#', '####.', '#.#..', '#..#.', '#...#'],
  S: ['.####', '#....', '#....', '.###.', '....#', '....#', '####.'],
  T: ['#####', '..#..', '..#..', '..#..', '..#..', '..#..', '..#..'],
  U: ['#...#', '#...#', '#...#', '#...#', '#...#', '#...#', '.###.'],
  V: ['#...#', '#...#', '#...#', '#...#', '#...#', '.#.#.', '..#..'],
  Y: ['#...#', '#...#', '.#.#.', '..#..', '..#..', '..#..', '..#..'],
  ' ': ['.....', '.....', '.....', '.....', '.....', '.....', '.....'],
};

const text = (x, y, s, c) => {
  [...s.toUpperCase()].forEach((ch, i) => {
    FONT[ch]?.forEach((row, j) => {
      [...row].forEach((px, k) => {
        if (px === '#') set(x + i * 6 + k, y + j, c);
      });
    });
  });
};

const centeredText = (y, s, c) => text((W - (s.length * 6 - 1)) / 2, y, s, c);

// ---------- the critter, stamped from ic_alive_character.xml ----------

const CRITTER_PINK = [
  [3, 5, 6], [4, 4, 8], [5, 4, 8], [6, 4, 8], [7, 2, 2], [7, 4, 8], [7, 12, 2],
  [8, 2, 2], [8, 4, 8], [8, 12, 2], [9, 4, 8], [10, 5, 6], [11, 5, 2], [11, 9, 2],
  [12, 5, 2], [12, 9, 2],
];
const CRITTER_INK = [[5, 5, 2, 3], [5, 9, 2, 3]];

const critter = (x, y, k) => {
  CRITTER_PINK.forEach(([row, col, w]) => rect(x + col * k, y + row * k, w * k, k, 's'));
  CRITTER_INK.forEach(([row, col, w, h]) => rect(x + col * k, y + row * k, w * k, h * k, '#'));
};

// ---------- composition ----------

frame(2, 2, W - 4, H - 4, 2, 0, '#');

// The statement, three lines, at the top of the page. The middle one is the
// app's own green: it is the only word here that is about something being done
// to you, and gold on cream cannot be read at poster size.
centeredText(8, 'YOUR PHONE HAS', '#');
centeredText(17, 'TAKEN OVER', 'f');
centeredText(26, 'YOUR LIFE', '#');

// The phone: a slab taller than it is wide, with a lit screen.
const PHONE = { x: 28, y: 37, w: 40, h: 46 };
frame(PHONE.x, PHONE.y, PHONE.w, PHONE.h, 2, 3, '#');
roundedRect(PHONE.x + 5, PHONE.y + 6, PHONE.w - 10, PHONE.h - 12, 2, 'w');
rect(PHONE.x + 15, PHONE.y + 2, 10, 2, '#'); // the earpiece slit

// The face: wide awake, then a grin full of teeth.
const eyeY = PHONE.y + 15;
oval(PHONE.x + 11, eyeY, 2, 3, '#');
oval(PHONE.x + 28, eyeY, 2, 3, '#');

const mouth = { x: PHONE.x + 7, y: PHONE.y + 22, w: PHONE.w - 14, h: 12 };
for (let j = 0; j < mouth.h; j++) {
  const inset = Math.round((j / (mouth.h - 1)) ** 1.7 * (mouth.w / 2 - 1));
  rect(mouth.x + inset, mouth.y + j, mouth.w - inset * 2, 1, '#');
}
for (let k = 0; k < 4; k++) { // four clean teeth hanging from the top of the grin
  const tx = mouth.x + 2 + k * 6;
  rect(tx, mouth.y + 1, 5, 1, 'w');
  rect(tx + 1, mouth.y + 2, 3, 1, 'w');
  rect(tx + 2, mouth.y + 3, 1, 1, 'w');
}

// The claws: an arm out of the phone's own side, swinging wide and curling back
// over the critter, finished by two fingers. They are filled shapes, not stroked
// lines - a tapered stroke breaks into dashes at this size and reads as a
// bracket, and a bracket is not a grip.
const claw = (mirror) => {
  const mx = (x) => (mirror ? W - 1 - x : x);
  const bend = (points) => points.map(([x, y]) => [mx(x), y]);
  ribbon(bend([[28, 68], [16, 76], [10, 88], [14, 99], [24, 104]]), [4, 3.6, 3, 2.4, 1.6], '#');
  ribbon(bend([[24, 104], [31, 100], [35, 94]]), [1.8, 1.4, 1.0], '#');
  ribbon(bend([[24, 104], [32, 108], [37, 106]]), [1.8, 1.4, 1.0], '#');
};
claw(false);
claw(true);

// The critter, small and standing under the thing that took its time.
const K = 2;
const spriteX = (W - 16 * K) / 2;
const spriteY = 84;
critter(spriteX, spriteY, K);
rect(spriteX + 3 * K, spriteY + 13 * K, 10 * K, K / 2, 'd'); // the ground it is on

// ---------- output ----------

if (!process.argv.includes('--quiet')) console.log(grid.map((row) => row.join('')).join('\n'));

const png = (w, h, scale, rgbAt) => {
  const crcTable = Array.from({ length: 256 }, (_, n) => {
    let c = n;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    return c >>> 0;
  });
  const chunk = (type, data) => {
    const len = Buffer.alloc(4);
    len.writeUInt32BE(data.length);
    const body = Buffer.concat([Buffer.from(type, 'ascii'), data]);
    let c = 0xffffffff;
    for (const byte of body) c = crcTable[(c ^ byte) & 0xff] ^ (c >>> 8);
    const tail = Buffer.alloc(4);
    tail.writeUInt32BE((c ^ 0xffffffff) >>> 0);
    return Buffer.concat([len, body, tail]);
  };
  const stride = w * scale * 3;
  const raw = Buffer.alloc((stride + 1) * h * scale);
  for (let y = 0; y < h * scale; y++) {
    raw[y * (stride + 1)] = 0; // filter: none
    const gy = Math.floor(y / scale);
    for (let x = 0; x < w * scale; x++) {
      const [r, g, b] = rgbAt(Math.floor(x / scale), gy);
      const o = y * (stride + 1) + 1 + x * 3;
      raw[o] = r;
      raw[o + 1] = g;
      raw[o + 2] = b;
    }
  }
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(w * scale, 0);
  ihdr.writeUInt32BE(h * scale, 4);
  ihdr[8] = 8; // bit depth
  ihdr[9] = 2; // colour type: truecolour
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', ihdr),
    chunk('IDAT', deflateSync(raw, { level: 9 })),
    chunk('IEND', Buffer.alloc(0)),
  ]);
};

const rgbCache = new Map();
const rgbAt = (x, y) => {
  const hex = PAL[grid[y][x]] ?? PAL['.'];
  let rgb = rgbCache.get(hex);
  if (!rgb) {
    rgb = [parseInt(hex.slice(1, 3), 16), parseInt(hex.slice(3, 5), 16), parseInt(hex.slice(5, 7), 16)];
    rgbCache.set(hex, rgb);
  }
  return rgb;
};

const out = join(dirname(fileURLToPath(import.meta.url)), 'alive-taken-over.png');
writeFileSync(out, png(W, H, CELL, rgbAt));
console.log(`\nwrote ${out} (${W * CELL}x${H * CELL})`);
