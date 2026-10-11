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
