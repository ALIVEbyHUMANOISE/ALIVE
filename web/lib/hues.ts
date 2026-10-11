import type { SpriteName } from './sprites'

/**
 * Which hue sits where - the website's copy of `ui/QuestHues.kt` and
 * `ui/PixelMarks.kt`.
 *
 * Every class name is written out in full here, because Tailwind deletes any
 * utility it cannot find as literal text in a scanned file. A registry that
 * assembled `bg-${key}` would compile, render, and quietly show white - which is
 * exactly the bug this pass exists to kill, and `scripts/guard.mjs` is what keeps
 * it dead.
 *
 * The rules are the app's, measured by `AlivePaletteTest` rather than eyeballed:
 * - `cat-*` and `amber` hold ink at 4.5:1, so they may carry a pixel mark.
 * - `*-mid` and `forest` never carry text. Ink on `blue_work_mid` measures 4.3:1,
 *   which is why `work` has its own tile hue instead of borrowing the blue of a
 *   score, and why a filled shape always gets its label from outside itself.
 * - `cat-*` is chrome, not meaning: a card's tab and its tile, nowhere else.
 * - Colour is never the only signal - the words are always next to it.
 */

/** A fill with no words in it: a tab, a swatch, a bar's interior, a tile's face. */
export const fills = {
  gold: 'bg-gold-mid',
  goldDeep: 'bg-gold-dark',
  red: 'bg-red-mid',
  redDeep: 'bg-red-dark',
  blue: 'bg-blue-mid',
  blueDeep: 'bg-blue-dark',
  forest: 'bg-forest',
  resting: 'bg-soft-forest',
  folded: 'bg-warm-gray',
  amber: 'bg-amber',
  critter: 'bg-pink',
  ink: 'bg-ink',
  paper: 'bg-white'
} as const

/** A face wide enough to sit sentences on: the role pales and the critter's pink. */
export const faces = {
  gold: 'bg-gold-pale',
  red: 'bg-red-pale',
  blue: 'bg-blue-pale',
  blueWork: 'bg-blue-soft',
  resting: 'bg-soft-forest',
  critter: 'bg-pink',
  paper: 'bg-white'
} as const

/** A kind of doing, as the app names it: one hue and one shape, handed out together. */
export const categories = {
  study: { fill: 'bg-cat-study', mark: 'book' },
  reading: { fill: 'bg-cat-reading', mark: 'books' },
  exercise: { fill: 'bg-cat-exercise', mark: 'dumbbell' },
  walking: { fill: 'bg-cat-walking', mark: 'footprints' },
  cleaning: { fill: 'bg-cat-cleaning', mark: 'broom' },
  creative: { fill: 'bg-cat-creative', mark: 'brush' },
  coding: { fill: 'bg-cat-coding', mark: 'terminal' },
  writing: { fill: 'bg-cat-writing', mark: 'pen' },
  mindfulness: { fill: 'bg-cat-mindfulness', mark: 'lotus' },
  social: { fill: 'bg-cat-social', mark: 'people' },
  work: { fill: 'bg-cat-work', mark: 'briefcase' },
  other: { fill: 'bg-cat-other', mark: 'target' }
} as const satisfies Record<string, { fill: string; mark: SpriteName }>

/** The shapes the app scatters - three of them, one row, one per beat. */
export const accentSprites: SpriteName[] = ['star', 'spark', 'diamond', 'heart', 'invader', 'plus']

/**
 * One accent per beat, for the badge only.
 *
 * A badge is the smallest thing on a panel that is allowed to hold a sentence, so
 * it wears a pale and reads at full contrast. The cards under it stay the app's
 * paper white and let their tabs carry the hue.
 */
const badgeByTone = {
  pink: 'critter',
  red: 'red',
  gold: 'gold',
  blue: 'blue',
  forest: 'resting'
} as const

export type Tone = keyof typeof badgeByTone

export const badgeFace = (tone: Tone): string => faces[badgeByTone[tone]]
