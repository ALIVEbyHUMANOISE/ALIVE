# ALIVE

ALIVE counts the entertainment time on your phone, gives you a daily allowance
that you set yourself, and closes those apps when the allowance is gone. It also
keeps a room for the hours it saves: focus sessions, daily missions, a widget.

Nothing about your day leaves the device. There is no account, no server, no
analytics — and the manifest declares no `INTERNET` permission, so the app cannot
send anything even if it wanted to. That is a claim about the build, not a
promise: it is checked by the permission list Android shows you.

## Where the release lives

| Thing | Where |
| --- | --- |
| Website | built from `web/`, published by `.github/workflows/site.yml` to GitHub Pages |
| APK | the `alive.apk` asset of the newest GitHub Release; the site's download button points at `releases/latest/download/alive.apk` |
| Privacy policy | `/privacy/` on the site, server-rendered in Indonesian and English |
| Contact | `alivebyhumanoise@gmail.com` |

## Layout

```
app/        the Android app (Kotlin, no third-party SDKs beyond AndroidX + Material)
web/        the download site — see web/README.md
guardian/   generate.mjs draws the 48x48 Guardian and writes the app's drawables,
            the site's PNG and a preview sheet from one source
poster/     generate.mjs draws the "your phone has taken over your life" poster
```

## Building

`java` is not on the PATH on this machine, so point Gradle at the Studio runtime:

```bash
JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew assembleDebug
JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew testDebugUnitTest
```

The site is a static export and needs to be told two addresses before it will
build — where it will be served from, and where its APK lives:

```bash
cd web && npm ci
NEXT_PUBLIC_SITE_URL=https://<host> NEXT_PUBLIC_APK_URL=https://github.com/<owner>/<repo>/releases/latest/download/alive.apk npm run build
```

`scripts/guard.mjs` runs as `postbuild` and fails the build when either address is
missing, when Tailwind silently deletes a colour class, or when the emitted
`robots.txt` / `sitemap.xml` still advertise `localhost`.

## Signing

`assembleRelease` reads `keystore.properties` from the repository root and is
deliberately silent about it: that file and every `*.jks` are gitignored, and the
release key is not in this repository or in CI.

Without it the release variant still compiles, **unsigned** — an unsigned APK
cannot be installed, so the mistake surfaces on the build machine instead of on a
user's phone. There is no debug-key fallback on purpose: that APK installs
happily and only reveals itself at the first update, when the only fix left is an
uninstall.

## Permissions ALIVE asks for

- **Usage access** — how long an app has been on screen, read locally.
- **Accessibility** — closes entertainment apps when the allowance is spent. This
  is the strongest of the five and it is only on when the user switches it on.
- **Display over other apps** — one recovery card, only while protection is off.
- **Notifications** — one ongoing notification, which asks for the protection the
  user switched off.
- **Physical activity** — the step counter, only for missions that prove it.
  Location is never requested.
