/**
 * One accent per beat.
 *
 * The app already says what each colour answers - gold is what was earned, red
 * is what is running out, blue is what was worked for, soft forest is what is
 * resting - so a panel borrows exactly one of them and its cards agree with its
 * badge. A page where every card is a different hue is not colourful, it is
 * noise; a page where one beat owns one hue reads as a story.
 */
export const tones = {
  pink: 'bg-pink',
  red: 'bg-red-pale',
  gold: 'bg-gold-pale',
  blue: 'bg-blue-pale',
  forest: 'bg-soft-forest'
} as const

export type Tone = keyof typeof tones

/** A card face for the beat, or plain white when the beat is the news itself. */
export const face = (tone: Tone): string => tones[tone]

/**
 * The four role pales, in the order a row of cards takes them.
 *
 * The app is not one colour per screen - it is a board where every mission
 * wears its own hue, and a site that paints a whole row the same shade reads as
 * a spreadsheet. So a row of cards cycles through the family: red for what runs
 * out, gold for what is earned, blue for what is worked for, forest for what
 * rests. The badge above the row still names the beat, so the page has an
 * anchor even when its cards do not all match.
 */
export const row: Tone[] = ['red', 'gold', 'blue', 'forest']

export const rowFace = (index: number): string => tones[row[index % row.length]]
