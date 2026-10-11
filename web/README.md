# ALIVE — the site

The download page for ALIVE, in the same visual language as the app. Nine beats
in the order the app tells its story: the day, what eats it, the number that
proves it, the choice, the room that opens up, the Guardian, the download. Plus
`/privacy/`, which is a server-rendered document rather than a panel, because a
store reviewer and a crawler have to read it without running a script.

It is a static export. No server, no database, no analytics — the same promise the
app makes about your data.

## Run it

```bash
npm install
npm run dev          # http://localhost:3000
```

`dev` needs nothing else. A **build** needs two addresses, because a static export
has no idea where it is going to live:

```bash
NEXT_PUBLIC_SITE_URL=https://<owner>.github.io/<repo> \
NEXT_PUBLIC_APK_URL=https://github.com/<owner>/<repo>/releases/latest/download/alive.apk \
npm run build
```

`scripts/guard.mjs` runs after the build and fails it if either is missing, if a
colour class Tailwind was never told about got dropped, or if the emitted
`robots.txt` / `sitemap.xml` / page still advertise `localhost`. A site that hands
crawlers a localhost canonical is a site that cannot be found, and that mistake
costs nothing to compile.

## Where its pieces come from

Nothing here is a hand-copied second version of the app, because those drift.

| Thing | Source | Command |
| --- | --- | --- |
| Colours (`app/tokens.css`) | `app/src/main/res/values/colors.xml` | `npm run sprites` |
| Characters and marks (`lib/sprites.ts`) | the app's 16×16 `VectorDrawable` grids | `npm run sprites` |
| Fonts (`public/fonts/`) | the TTFs the app bundles | copy from `app/src/main/res/font/` |
| The Guardian (`public/images/guardian.png`) | `guardian/generate.mjs`, the same 48×48 grid the app draws | `node guardian/generate.mjs` |

Run `npm run sprites` after changing a colour or a sprite in the app.

## The APK

The download button does not serve a file from this folder. It points at
`releases/latest/download/alive.apk` on the GitHub repository, so:

- a new version is a release, not a redeploy of the site;
- the 5 MB binary never enters the site's history or its deploy;
- a beta user lands on a release page that says which version they are fetching.

That address is an environment variable, not a string in a component — see
`lib/site.ts`. Cutting the release itself is a local act: `./gradlew assembleRelease`
signs with a key that is deliberately not in this repo and deliberately not in CI.

## Ship it

```bash
npm run build        # static files in out/
```

`.github/workflows/site.yml` is the only publisher: it builds the export and hands
`out/` to GitHub Pages. A project page is served from under `/<repo>/`, so the
workflow derives both the crawler's address and Next's asset prefix from the
repository it runs on — one value, never two that can disagree.

## Layout

```
app/          layout, page, globals.css, generated tokens.css, privacy/, robots.ts, sitemap.ts
components/   Header, Panel, PixelSprite, Reveal, MockWidget, LangProvider, Site, ui
  sections/   Story.tsx (01-03), Answer.tsx (04-09 + footer)
lib/          copy.ts (every sentence, ID + EN), site.ts (the addresses), sprites.ts (generated), hues.ts
scripts/      sprites.mjs, guard.mjs
public/       fonts/, images/
```

Language is a browser preference (`localStorage`, default Indonesian) — the page
is one URL, and the copy file keeps both languages the same shape so a line added
in one is a compile error in the other. The consequence worth knowing: a crawler
sees the Indonesian page, because the switch happens after the HTML arrives.
