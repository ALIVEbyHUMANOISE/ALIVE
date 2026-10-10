# ALIVE Design Constitution v1

> **Designed like a human made it, not generated like a machine made it.**

ALIVE should feel like a living sketchbook/editorial experience — not a generic SaaS product, AI landing page, or polished corporate productivity app.

---

## 01 — Core Principle

ALIVE must prioritize:

**Human feeling → Storytelling → Clarity → Aesthetic**

Not:

**Decoration → Complexity → Visual effects**

The visual system exists to support ALIVE's mission and experience, not to compete with it.

---

## 02 — The Imperfection Rule

Ketidaksempurnaan adalah bagian dari identitas ALIVE.

Use:
- slightly irregular lines
- organic shapes
- natural spacing
- organic character poses
- handwritten annotations
- small sketches and scribbles
- rough outlines
- subtle variation in line weight
- imperfect hand-drawn elements

But:

> **Imperfect ≠ messy.**

The interface must remain clear and usable.

---

## 03 — Line Language

Main illustrations use an **ink drawing** language.

Characteristics:
- ink-style outlines
- organic strokes
- rounded line endings
- slightly imperfect precision
- avoid ultra-clean corporate SVG/vector illustration
- use hatching/scribble shading when needed instead of gradients

The result should feel like:

> ✏️ “drawn by a human”

not:

> 📐 “constructed by vector software.”

---

## 04 — Character Rule

The ALIVE master character must remain visually consistent.

Character:
- simple stickman/doodle
- simple round head
- line-based body and limbs
- minimal facial expression
- not overly cute
- not chibi
- not realistic
- no excessive facial detail

The character can change **pose and emotional state**, while keeping the same visual identity.

Example progression:

`trapped → unaware → struggling → realizing → reclaiming → alive`

---

## 05 — Illustration ≠ Decoration

Every major illustration should have a **storytelling function**.

Do not add an illustration merely because an area feels empty.

Illustrations should help communicate an idea, emotion, state, or transition.

Examples:
- Character holding a phone → dependence
- Phone becoming increasingly dominant → the phone taking over life
- Character moving away from the phone → reclaiming control

---

## 06 — Whitespace Rule

**Whitespace is part of the design.**

Do not be afraid of empty space.

ALIVE may intentionally use:
- a single sentence
- one character
- one illustration
- large areas of empty space

Storytelling needs **breathing room**.

---

## 07 — Color Discipline

### ALIVE Palette

Nine colours. Each one answers a question the other eight cannot.

| Role | Answers | Hex |
|---|---|---|
| Paper | Where am I? | `#F5F1E8` |
| Ink | What is written? | `#1D1D1B` |
| Forest | What is ALIVE offering? | `#315C45` |
| Soft Forest | What is resting? | `#DCE5DC` |
| Warm Gray | What is secondary, or not really here? | `#D8D2C7` |
| White | What is a face? | `#FFFDF8` |
| Gold | What did I actually earn? | mid `#E0A526`, dark `#6F4E0C`, pale `#F6E7C2` |
| Red | What is running out? | mid `#D9553F`, dark `#9E2F22`, pale `#F3DAD3` |
| Blue | What was worked for? | mid `#4E86B4`, dark `#27537A`, pale `#D9E5EE` |

Gold, red and blue arrived late, and they had to argue for it: the palette read
as one beige wall, and a game that keeps score needs a colour for a score. They
were added for **data, chrome, illustration and reward** — nowhere else.

Rule:

> **Don't add a new colour unless a role exists that no colour already plays.**

And the second rule, which is what the first one costs:

> **A role colour must pass 4.5:1 as text against every surface it will sit on.**

That is measured, not eyeballed — `AlivePaletteTest` runs the numbers so a
future tweak cannot quietly make a label unreadable.

Red is a threshold, not a decoration: it appears exactly where the app already
states the same number as critical. Colour is never the only signal — the
minutes are printed next to it.

### The ground and the surfaces

The table says Paper answers "where am I", and for a while that was true of every
screen — until the page ground moved to `canvas` (`#F1EBDC`), one shade darker,
because a white card is only a face if there is something to be a face *against*.
Two creams then started trading jobs, and the statistics panel ended up wearing
`paper` as its background: a visibly whiter screen than the tabs either side of
it, for no reason anyone could name.

The rule, so it cannot drift back:

> **`canvas` is the ground — every page stands on it, tabs and panels alike.
> `paper` is a surface: the widget's card, the inside of an empty chart, the
> bar's body.**

### The category family

Nine more colours, and they are a different kind of thing: one hue per category
(study amber, reading sky, exercise green, walking lime, cleaning grey, creative
pink, coding violet, writing orange, work blue), and each one lives on exactly
two objects — the tab on a quest card's top edge, and the tile its pixel mark
sits in.

> **A category hue is chrome. Never text, never status, never a button.**

That boundary is what lets nine of them coexist with the roles above without the
screen turning into a crayon box. Ink clears 4.5:1 on every one of them, because
the marks drawn inside them are ink — `AlivePaletteTest` measures all ten plus
the action amber.

### Amber is the offer

The primary action is amber `#F7C948` with ink text. Forest is what ALIVE *is*
(the meter, the selected nav block, the chart's spent bars); amber is what it is
holding out. Reward gold is a deeper yellow (`#E0A526`) and never appears on a
button, so "press this" and "you earned this" cannot be confused.

---

## 08 — Typography

ALIVE has two typographic personalities.

### Editorial
Used for:
- headlines
- statements
- storytelling
- expressive text

### Functional
Used for:
- buttons
- navigation
- timers
- settings
- status
- data

Handwriting is **not** the primary font for the entire application.

Use handwriting selectively for:
- annotations
- arrows
- emphasis
- handwritten notes
- storytelling elements

---

## 09 — UI Precision Rule

**Hand-drawn does not mean every UI element should be messy.**

Functional interface elements must remain reliable and readable.

For example:
- timers must remain clear
- buttons must look and behave like buttons
- navigation must remain understandable
- status information must remain legible

Principle:

> **Storytelling can be imperfect. Interaction must remain reliable.**

---

## 10 — Anti-AI-Look Rule

ALIVE must avoid visual treatment that feels generic, synthetic, or machine-generated.

Avoid:
- generic AI illustrations
- polished vector mascots
- 3D characters
- glossy UI
- glassmorphism
- excessive gradients
- perfect geometric illustrations
- generic startup landing-page aesthetics
- overly cute productivity-app aesthetics
- stock-illustration aesthetics

If an element looks **too perfect**, consider making it more organic.

---

## 11 — No Visual Noise

Minimalism remains important.

Do not add:
- decoration everywhere
- doodles in every corner
- excessive icons
- excessive borders
- excessive cards
- excessive animation
- unnecessary colors

> **Hand-drawn ≠ lots of scribbles.**

The fewer elements there are, the more important each element becomes.

---

## 12 — App Icon Rule

The ALIVE app icon must be:

> **As minimal as possible.**

Target:

**1 character + 1 visual idea + background**

Avoid:
- ALIVE text
- slogan
- phone
- clock
- quest elements
- multiple doodles
- unnecessary detail

The icon must remain recognizable at very small sizes.

The app icon represents the **face of ALIVE**, not an explanation of every feature.

---

## 13 — ALIVE Must Feel Alive

The name ALIVE should be reflected through visual progression.

The same character can move through states such as:

**Disconnected**

↓

**Unaware**

↓

**Controlled**

↓

**Aware**

↓

**Reclaiming**

↓

**ALIVE**

The application should therefore feel like **one continuous story**, not a collection of unrelated UI screens that merely share the same colors.

---

## 14 — Weight and Rhythm

A box has two independent properties, and collapsing them is how every card on
a screen ends up shouting.

**Weight** — how loudly the eye should treat it: `MAJOR`, `MINOR`, `PLAIN`.
**Tone** — what happened: `NEUTRAL`, `HELD`, `LANDED`, `QUIET`.

> **A screen gets exactly one MAJOR.**

What earns it is something broken or something in flight — the guard gap, a
missing permission, a live quest — not whatever happened to render first.
Ordinary content sits on the canvas with no edge at all.

A closed door never wins: `QUIET` overrides weight down to a hairline and no
shadow, whatever asked.

No surface is dark. Weight comes from the edge and the shadow, never from an
inverted fill.

### The corner

Every face is rounded, and there are exactly five radii:

| Object | Radius |
|---|---|
| Card | 16dp |
| Field | 14dp |
| Tile, button | 12dp |
| Meter step, chart bar | 4dp |
| Badge, chip | a pill |

> **A sixth radius is not allowed — and neither is a square face.**

The shadow is 6dp on a MAJOR and 4dp on everything else. Both layers get the
corner: a square ink shadow behind a round card reads as two black corners,
which is the first thing anyone notices.

### Rhythm

Six numbers, one per relationship (`res/values/dimens.xml`):

| Token | Used for |
|---|---|
| `card_gap` 16dp | Air between cards |
| `group_gap` 12dp | Inside a card, between groups |
| `line_gap` 8dp | Between lines of one group |
| `pair_gap` 4dp | Between two things that belong together |
| `card_inset` 16dp | Inside a card's edge |
| `ledger_inset` 12dp | Inside a ruled table cell |

> **Nothing inside one card is `card_gap` or more apart.** If two things want
> that much air, they are two cards.

`card_gap` is wide so a MAJOR's 4dp shadow always lands on clean canvas: two
cards must never touch. `card_inset + ledger_inset` equals the page gutter, so a
tile inside a card lines up with the page edge instead of overhanging it.

Padding is measured from the **face**, not from the view's bounds — the right
and bottom carry the shadow's width as well, or a glyph ends up sitting on it.

---

## 15 — Type Scale

Eleven steps, one job each:

| Step | Size | Job |
|---|---|---|
| Timer | 56sp | The one big number on a screen |
| Display | 34sp | Wordmark, screen name |
| Number | 28sp | A ledger figure |
| Headline | 26sp | A statement worth its own line |
| Lead | 20sp | Card titles, in-flight facts |
| Body | 16sp | What is being read |
| Note | 16sp | Support and disclosure — same size, one step quieter |
| Annotation | 20sp | The handwritten aside |
| SectionHeader | 14sp bold | The name of a block |
| Meta | 13sp | A count beside a tile |
| Tag | 10sp pixel | A number with a unit |

Three faces, and they are not interchangeable: `sans-serif-black` carries
structure, `vt323` carries numbers, `press_start_2p` carries labels.

> **Say it in words, not in stamps.**

Uppercase with tracking is furniture — on every card title, button and nav label
it turns an app into a form. Names are sentence case, the way a page names a
section. The one exception is the pixel face, which is already shouting by
nature.

> **A size exists only if it does a job its neighbour cannot.**

Two steps within 2sp of each other are one step wearing a different name — that
is how a screen ends up with four "different" body sizes and no hierarchy.

And the pixel face has a hard limit:

> **Ten glyphs, and never a sentence.**

Below that size it stops being readable text. It used to carry the bottom
navigation and 32-character disclosures; both now use a face a person can read
at a glance.

Body prose is 16sp or larger, and no text may fall under the contrast floor
measured in `AlivePaletteTest`.

---

## 16 — The Game Layer

ALIVE keeps score, so it is allowed to look like a game — inside one budget.

**What may move on its own:** the masthead's one-cell bob, the wordmark's block
caret, and a 780ms burst of squares when a quest actually pays out. Everything
else is still until touched.

> **One celebration per earned thing, not per render.**

The app remembers which attempt it already celebrated; a reward that parties for
the fourth time is a glitch.

All of it stops when the device says *remove animated transitions*. That setting
is obeyed, not overridden.

**Gold means earned**, and only appears on: the reward chip, the streak pips, the
marker beside a finished quest, and one chart slice. Pips are the *shape* of a
number, never the number itself — the figure beside them stays authoritative.

There are no levels, no XP, no rarity, no badges, no streak scores, and no
confetti for something nobody did.

**Pixel geometry:** every sprite is a 16×16 grid and every sprite size snaps to
whole device cells, because a stretched pixel is not a pixel. Charts wrap into
balanced rows rather than shrinking past an 18dp slot, labels are printed only
where they actually fit, and the label floor is 8sp.

### Tiles, and what a choice is allowed to become

A category's mark sits **inside a tile of its own hue** — on the board, on the
detail page, in the history, and in the focus card. A bare glyph on canvas is a
decoration; the same glyph inside an edged, shadowed square is an object with a
name, and that is what a nine-item board gets scanned by.

A choice may be re-dressed as a tile but not re-built as one:

> **A single-choice picker stays a `RadioGroup` of `RadioButton`s.**

`buttonDrawable = null` plus a state-list face is enough. Dropping to clickable
views is how a picker loses its mutual exclusion, its D-pad, and the word
"selected" in TalkBack — three things a colour cannot give back.

---

## Design Constitution Principle

> **Change how ALIVE feels, not what ALIVE fundamentally does.**

The visual redesign must preserve the core functionality and enforcement engine established in earlier phases unless an integration change is genuinely required.
