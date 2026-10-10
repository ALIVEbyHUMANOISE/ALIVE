# ALIVE — the site

The download page for ALIVE, in the same visual language as the app. Seven panels
in the order the app tells its story: the day, what eats it, the number that
proves it, the choice, the room that opens up, the download.

It is a static export. No server, no database, no analytics — the same promise the
app makes about your data.

## Run it

```bash
npm install
npm run dev          # http://localhost:3000
```

## Where its pieces come from

Nothing here is a hand-copied second version of the app, because those drift.

| Thing | Source | Command |
| --- | --- | --- |
| Colours (`app/tokens.css`) | `app/src/main/res/values/colors.xml` | `npm run sprites` |
| Characters and marks (`lib/sprites.ts`) | the app's 16×16 `VectorDrawable` grids | `npm run sprites` |
| Fonts (`public/fonts/`) | the TTFs the app bundles | copy from `app/src/main/res/font/` |
| `public/apk/alive-debug.apk` | `app/build/outputs/apk/debug/` | `npm run apk` |

Run `npm run sprites` after changing a colour or a sprite in the app, and
`npm run apk` after building a new APK — the download button points at a file, so
a missing one is a broken page, not a fallback.

`public/apk/` is deliberately not in git: a rebuilt APK is a new 7 MB blob in the
history every time. Run `npm run apk` before `npm run build`, and the export
carries it. When the project gets a GitHub repository, the better shape is a
Release holding the APK and a deploy that downloads it — the link in
`components/sections/Answer.tsx` is the only thing that changes.

## Ship it

```bash
npm run build        # static files in out/
```

`out/` is a folder of plain files: drop it on any host. Keep `out/apk/` with it,
or the download link has nothing to point at.

## Layout

```
app/          layout, page, globals.css, generated tokens.css
components/   Header, Panel, PixelSprite, Reveal, MockWidget, LangProvider, Site
  sections/   Story.tsx (01-03), Answer.tsx (04-07 + footer)
lib/          copy.ts (every sentence, ID + EN), sprites.ts (generated)
scripts/      sprites.mjs, apk.mjs
public/       fonts/, apk/
```

Language is a browser preference (`localStorage`, default Indonesian) — the page
is one URL, and the copy file keeps both languages the same shape so a line added
in one is a compile error in the other.
