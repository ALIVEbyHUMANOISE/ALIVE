# ALIVE Living Interface — Major Update Report

Tanggal: 2026-10-29 · Branch `master` · HEAD sebelum perubahan: `b29c3db`
Repository aktual: `C:/Users/ftnaditiaazyrexx/AndroidStudioProjects/projectego`
Status kerja: **belum di-commit** (sesuai aturan "jangan membuat commit kecuali diminta").
Build release **terpasang di perangkat** 2026-10-29 20:56, md5 terverifikasi (lihat §11).

---

## 0. Status progres

| Paket | Isi | Status |
|---|---|---|
| A — correctness | Baseline diverifikasi, 7 temuan audit diklasifikasi ulang, UI dipisah dari state enforcement, lifecycle & jadwal laporan diperbaiki, regresi ditulis lebih dulu | **Selesai** — 706 test hijau |
| B — morph & visual | Kontinuitas konten saat morph, lampu lebih menyala dengan shader di-cache, jarak simetris, target sentuh 48dp, panel 320dp, karakter tetap kontekstual | **Selesai di kode + teruji** — rasa di perangkat masih menunggu matanya |
| C — integrasi & rilis | Suite penuh, `assembleDebug`, `assembleRelease` (+ verifikasi signature), review diff independen, laporan ini, pemasangan ke HP | **Selesai, kecuali commit** (tidak diminta) |

Yang tersisa dan butuh keputusan dia: penilaian reversal morph di HP, pemilik rollover harian
(§12 no. 2), dan 20 error lint lama (§10).

---

## 1. Ringkasan perubahan

Living Interface (pill) diperbaiki pada tiga lapis:

1. **Koreksi state** — pembacaan pemakaian untuk UI dipisah dari pembacaan otoritatif
   enforcement, dan jadwal laporan tidak lagi bisa macet karena satu callback hilang.
2. **Lifecycle** — layar mati, start ulang service, dan munculnya Activity ALIVE sendiri
   kini menutup overlay dengan segera, bukan setelah satu tick 20 detik.
3. **Visual & morph** — ketiga lampu tetap berwarna dengan halo yang benar-benar terlihat,
   jarak simetris, target sentuh 48dp, panel dibatasi 320dp, dan konten panel tidak
   re-layout pada setiap frame animasi.

Tidak ada dependensi baru, tidak ada aset baru, tidak ada perubahan `applicationId`,
signing, atau aturan pembatasan. Enforcement tidak diubah sama sekali
(`git diff` pada `EgoAccessibilityService.kt`, `ForegroundEnforcementPolicy.kt`,
`GuardianAlertActivity.kt` kosong).

---

## 2. Temuan audit yang terkonfirmasi

Klasifikasi terhadap 7 temuan audit sebelumnya, diverifikasi pada kode aktual.

| # | Temuan | Status | Bukti |
|---|---|---|---|
| 1 | Pembacaan statistik oleh UI memicu side effect state | **CONFIRMED BUG** | `UsageTracker.readWindow()` memanggil `computeReport(recordHistory = true)`; di dalamnya `lockStateManager.checkAndPerformDailyReset()` tak bersyarat, latch `setLocked(true/false)` lewat `UsageSourcePolicy.latchAction`, dan `usageHistory.recordDay(...)`. `InfoPillService.requestReport()` memanggil `readTodayEntertainmentUsage()` di worker. |
| 2 | Morph tidak mempertahankan kontinuitas saat dibatalkan | **ALREADY FIXED** (untuk posisi/kecepatan) + **CONFIRMED BUG** (reflow konten) | Carry-over posisi+kecepatan ada di `PillMorphEngine.springRun`, `stopMorph()` melepas referensi sebelum `cancel()`, `commit()` dijaga flag `committed`/`discarded`/identitas `sizeAnimator`. Yang belum: panel `MATCH_PARENT` sehingga terukur ulang tiap frame. |
| 3 | Lifecycle `PillReportSchedule` bisa menghentikan pembaruan laporan | **CONFIRMED BUG** | `onStartCommand` memanggil `ticker.removeCallbacksAndMessages(null)` yang juga menghapus callback `applyReport` milik read yang sedang berjalan → `inFlight` tidak pernah bebas → `request()` menolak semua tick berikutnya. Selain itu `complete(token)` menerima token tak dikenal dan bisa menghapus slot milik read lain. |
| 4 | Overlay tidak langsung hilang saat layar perlindungan aktif | **CONFIRMED BUG** | Tidak ada pengamatan Activity; penutupan hanya lewat tick 20 detik atau `stopSelf()` eksplisit di `GuardianAlertActivity`. `onDestroy` masih menganimasikan window keluar 160 ms, sehingga surface sentuh tertinggal setelah service mati. |
| 5 | Deteksi layar mati terlambat | **CONFIRMED BUG** | `isInteractive` hanya diperiksa di `refresh()` (per 20 s) dan tidak ada `ACTION_SCREEN_OFF` receiver; start saat layar tidur tetap memasang overlay setelah delay 800 ms. |
| 6 | Representasi visual traffic light & penempatan | **CONFIRMED** | Slot 14dp dengan core 10dp menyisakan halo 2dp → tampil matte; `RadialGradient` dialokasi pada setiap `onDraw`; padding header 14dp kiri / 12dp kanan (asimetris). |
| 7 | Target sentuh & clipping saat animasi | **CONFIRMED** untuk target sentuh | Tinggi header terukur 36dp (< 48dp). Clipping saat ini disengaja (root `clipChildren = true`); yang tidak disengaja adalah re-flow teks, diperbaiki di #2. |

Temuan baru di luar daftar audit:

- **CONFIRMED BUG — cache laporan lintas hari.** `applyReport()` menyimpan hasil berapa pun
  tuanya dan mencatat `lastReportAtElapsed` pada saat *diterapkan*, bukan saat dibaca,
  sehingga report kemarin bisa tampil sebagai hari ini setelah midnight.

## 3. Temuan yang tidak terbukti / masih belum pasti

- **INCONCLUSIVE — pembacaan otoritatif basi membuka latch.** Worker guard dapat
  menyelesaikan read ber-usage rendah setelah enforcement mengunci, dan `latchAction`
  akan menghasilkan `CLEAR`. Jalur ini **tidak diubah** karena menyentuh enforcement;
  pill sudah tidak lagi ikut-ikutan menulis latch, jadi permukaan risikonya mengecil.
  Belum direproduksi pada perangkat.
- **ALREADY FIXED (sebagian) — metadata history lintas hari.** `recordDay` memakai
  `dayKeyOf(startTime)` window yang sama, tetapi arsip sebelum retry midnight tetap
  memakai allowance "sekarang". Dibiarkan: perubahan menyentuh jalur penulisan history.
- **NOT REPRODUCIBLE — "reset harian dipicu UI" sebagai kehilangan data.** Reset harian
  itu sendiri sah dan tetap berjalan; yang salah hanyalah bahwa UI menjadi pemicunya.
  Reset tidak dihapus.

---

## 4. File yang diubah dan alasannya

**Produksi**

- `app/src/main/java/com/projectego/app/domain/UsageTracker.kt`
  — tambah `readTodayEntertainmentSnapshot()` (read-only: tanpa reset, tanpa latch,
  tanpa history; menolak hari yang belum selaras sebelum dan sesudah query) dan parameter
  internal `resetDailyState`. `readTodayEntertainmentUsage()` dan
  `calculateTodayEntertainmentUsage()` tidak berubah perilakunya.
- `app/src/main/java/com/projectego/app/data/LockStateManager.kt`
  — tambah `isTrackingDay(dayKey)` read-only yang tidak menginisialisasi state.
- `app/src/main/java/com/projectego/app/domain/PillReportSchedule.kt`
  — `complete()` hanya mengakui token yang sedang aktif; token asing tidak bisa
  membebaskan slot maupun mempublikasikan hasil.
- `app/src/main/java/com/projectego/app/service/InfoPillService.kt`
  — memakai snapshot; callback arrival/tick bernama agar start ulang tidak menyapu
  completion laporan; receiver `ACTION_SCREEN_OFF`; `ActivityLifecycleCallbacks` in-process;
  `dismiss()` melepas window seketika; penjagaan hari pada cache laporan; `minimumHeight`
  header 48dp; padding simetris; slot lampu 18dp; lebar panel maks 320dp; tile karakter
  64dp/sprite 48dp; `holdPanelWidth()`/`releasePanelWidth()` untuk kontinuitas morph.
- `app/src/main/java/com/projectego/app/service/TrafficLightDot.kt`
  — halo lebih lebar dan lebih terang, rim tipis di tepi core, shader di-cache dan
  dimodulasi lewat alpha paint (tidak ada alokasi per frame), setter tidak meng-invalidate
  bila nilainya tidak berubah.

**Test**

- `app/src/test/java/com/projectego/app/domain/PillReportScheduleTest.kt` — 2 kasus token asing.
- `app/src/test/java/com/projectego/app/domain/UsageSnapshotTest.kt` — 19 kasus (baru).
- `app/src/test/java/com/projectego/app/service/InfoPillServiceTest.kt` — 10 kasus lifecycle
  (baru).
- `app/src/test/java/com/projectego/app/service/PillLightsTest.kt` — 4 kasus geometri + pixel
  lampu (baru).
- `app/src/test/java/com/projectego/app/service/PillPanelTest.kt` — 5 kasus panel/morph (baru).
- `app/src/test/java/com/projectego/app/service/PillFrameCapture.kt` — generator bukti frame
  (bukan test perilaku; hanya assertion "tanah island hitam pekat" dan file tertulis).

---

## 5. Perubahan state model

Model state lama (`expanded` + `currentFrame` + `morphEngine` + `sizeAnimator`) dipertahankan;
tidak ada enum atau controller baru. Yang berubah:

- **Kepemilikan kebenaran.** UI hanya membaca snapshot. Sumber kebenaran enforcement tetap
  `readTodayEntertainmentUsage()` milik guard dan klaim Time Bank.
- **Aging cache laporan.** `lastReportDay` kini menyimpan hari milik report; report dari hari
  lain dibuang sebelum dipakai, dan permintaan ulang dijadwalkan, bukan diterima apa adanya.
- **Penutupan overlay.** Ditambah jalur penutupan eksplisit (`dismiss()`) dari tiga pemicu:
  layar mati, Activity ALIVE sendiri mulai, dan start saat layar tidak interaktif.
  `isRunning` menjadi syarat publish, sehingga hasil dari service yang sudah dismissed tidak
  menulis state lagi.

## 6. Perubahan animasi

- Tidak ada perubahan kurva atau durasi: tetap 260/210 ms ease, spring λ=40 untuk interupsi,
  900 ms cap. `PillMorphEngineTest` (tidak diubah) tetap hijau.
- **Kontinuitas konten:** panel dikunci pada lebar tujuan selama morph berjalan lalu
  dikembalikan ke `MATCH_PARENT` saat commit, sehingga konten ter-layout sekali dan di-clip
  oleh shell — bukan re-wrap setiap frame.
- **Teardown tidak dianimasikan:** window dilepas dengan `removeViewImmediate`. Animasi keluar
  160 ms sebelumnya adalah surface sentuh yang tertinggal, persis yang dilarang aturan A4.
- **Reduced motion:** jalur tanpa animator tetap menulis state akhir dan kini juga melepas
  kunci lebar panel, jadi tidak ada state setengah jadi.

## 7. Perubahan visual

- Hitam pekat dipertahankan sebagai tanah island; radius 26dp/34dp tidak diubah.
- Ketiga lampu: core 10dp di dalam slot 18dp, halo 4dp dengan alpha puncak 0.66 (sebelumnya
  0.42 pada ring 2dp) plus rim 1.2dp. Semua core tetap penuh warna di semua state; hanya halo
  merah yang berdenyut.
- Jarak simetris 14dp: tepi pill → timer, timer → lampu, lampu → tepi pill.
- Header pill minimum 48dp sebagai target sentuh.
- Panel: lebar maksimum 320dp (sebelumnya 360dp), tile karakter 64dp dengan sprite 48dp.
  Struktur informasi, hierarki, dan seluruh copy tidak diubah.
- Indikator kritis mengikuti kebijakan yang sudah ada (`criticalZoneTier >= 1` /
  `TimeStatus.CRITICAL` = denyut; limit = merah tegas; hari pemulihan = hijau), tanpa threshold
  baru. Dengan motion mati, denyut diganti ring statis (perilaku lama, kini teruji pixel).

## 8. Integrasi karakter

Sistem pose yang ada (`PillPosePolicy` → `AliveStates.faceSpriteFor`) dipakai apa adanya;
tidak ada sprite baru dan tidak ada nama file yang dikarang. Yang diverifikasi pada render
nyata: YouTube → pose memegang HP; limit → wajah marah dengan burst merah; hari pemulihan →
tidur; konteks tak dikenal → `IDLE`/normal. Pose tidak diulang bila face-nya sama
(`applyCharacter` early-return), dan morph mengambil alih alpha karakter agar tidak ada
crossfade yang nyangkut di belakang shell.

## 9. Test yang dijalankan dan hasil aktual

Environment: Gradle 9.5 / AGP 9.3.2, daemon Java 25 (Studio JBR), Robolectric 4.16.1,
`JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"`.

| Perintah | Hasil |
|---|---|
| `./gradlew :app:test --rerun-tasks --offline` (baseline, sebelum perubahan) | **664 lulus, 0 gagal, 0 error, 0 skip** |
| `./gradlew :app:testDebugUnitTest --tests '*InfoPillServiceTest' --tests '*PillReportScheduleTest' --rerun --offline` | **15 test, 6 gagal** (merah: callback tersapu, screen-off, Activity sendiri, start saat layar mati, 2 kasus token) |
| `--tests '*PillLightsTest' --tests '*PillPanelTest' --rerun --offline` | **9 test, 6 gagal** (merah: jarak asimetris, halo matte, pulse, lebar panel saat morph, budget 320dp, tile karakter) |
| `--tests '*PillLightsTest' --tests '*PillPanelTest' --rerun --offline` (setelah implementasi) | **9 lulus, 0 gagal** |
| `./gradlew :app:test --rerun --offline` (akhir, lalu diulang `--rerun-tasks` bersih) | **706 lulus, 0 gagal, 0 error, 0 skip** (82 → 87 suite) |
| `./gradlew :app:test :app:assembleDebug --rerun-tasks --offline` | **BUILD SUCCESSFUL**, 46 task dieksekusi dari nol |
| `./gradlew :app:test :app:assembleDebug --rerun-tasks --offline` | **BUILD SUCCESSFUL** |

Test lama tidak ada yang diubah ekspektasinya; `PillMorphEngineTest`, `PillPanelPolicyTest`,
`PillTrafficPolicyTest`, `UsageReadTest`, dan `UsageDisplayReadTest` tetap utuh dan hijau.
`UsageSnapshotTest` memakai shadow `UsageStatsManager` hanya untuk menyuntik interleave
(latch ditulis *selama* query) dan simulasi midnight lewat perubahan zona waktu — semua
report, prefs, dan perhitungan charge aslinya.

**Test baru dibuktikan bisa gagal.** Setelah implementasi, dua perbaikan dikembalikan
sementara dan suite dijalankan lagi: `a pill restarted after a screen-off can read the day
again` gagal (`ArrayDeque is empty` — read kedua memang tidak pernah dijadwalkan) dan
`a settled collapse is compact again...` gagal (`expected -1 but was 960` — panel tetap
terkunci pada lebar panel). Keduanya hijau lagi setelah perbaikan dikembalikan.

**Review independen.** Sebuah pass review terhadap diff menemukan tiga cacat yang lalu
diperbaiki (yang dua pertama diuji di atas): `dismiss()` masih menyapu seluruh antrian
handler sehingga bisa menjebak slot laporan bila service di-restart sebelum dihancurkan;
`releasePanelWidth()` dipanggil di jalur yang salah (commit morph) sehingga refresh justru
menyembunyikannya; dan `reportSchedule.complete()` pernah dipanggil dari worker thread
padahal kelas itu sengaja tidak disinkronkan — jalur itu dihapus. Test lampu juga diubah
agar pita pengukurannya dalam dp (bukan px), sehingga tidak bisa lolos sendiri pada
density > 1.

## 10. Build

| Task | Hasil |
|---|---|
| `:app:assembleDebug --offline` | **SUCCESS** — `app/build/outputs/apk/debug/app-debug.apk` (6 657 425 B) |
| `:app:assembleRelease` | **SUCCESS** — `app/build/outputs/apk/release/app-release.apk` (5 382 327 B), `lintVitalRelease` lulus |
| Verifikasi signature release | `apksigner verify` → Signer #1 `CN=ALIVE, OU=Development, O=Humanoise, C=ID`. Key/tidak dibaca, hanya artefaknya. |
| `:app:lintDebug` | **GAGAL — 20 error, 111 warning.** Semua error ada di file yang tidak disentuh diff ini: `MissingTranslation` ×9 (`values/strings.xml`), `NewApi` ×8 (`themes.xml`/`values-night/themes.xml`), `MissingPermission` ×3 (`PromiseWatch`, `QuestDeadlineWatch`, `QuestWatch`). Karena input lint untuk file-file itu identik dengan HEAD, kegagalan ini lama, bukan regresi batch ini. |

## 11. Status pengujian perangkat nyata

**Build terpasang di perangkat: SUDAH. Perilaku overlay di perangkat: BELUM dinilai.**

Dipasang 2026-10-29 **20:56** pada SM-A075F (`R9RY804E6FD`) dengan `pm install -r`
(setelah push ke `/data/local/tmp`, file sementara dihapus):

- `md5sum base.apk` di perangkat = `cf1b207611c783049c225bb1225a3d82` = APK release lokal.
  Bukan sekadar "Success" dari installer.
- `lastUpdateTime=2026-10-29 20:56:29`; `versionCode` tetap **12**, jadi layar About di dalam
  app tidak menunjukkan perubahan — satu-satunya bukti bytes baru adalah md5 di atas.
- `EgoAccessibilityService` masih enabled **dan** ter-bind (`dumpsys accessibility`
  mencantumkan servicenya), `SYSTEM_ALERT_WINDOW: allow`, `GET_USAGE_STATS: allow`.
  Install `-r` tidak menghapus izin — yang butuh re-grant hanya uninstall + install baru.
- Build terpasang ini `retrieveInteractiveWindows=false` dan `eventTypes=
  TYPE_WINDOW_STATE_CHANGED` saja, konsisten dengan pernyataan di surat banding Play Protect
  bahwa layanan tidak membaca konten window.

Yang **tidak** dilakukan pada perangkat: input sentuh, screenshot, wake, dan apa pun yang
menyentuh data app — ponsel ini baca-only. Emulator `Pixel_10_Pro` sempat boot penuh
(`sys.boot_completed=1`, API 37) lalu mati sebelum sempat dipakai; tidak ada install ke sana.

Artinya daftar berikut masih **menunggu penilaian mata dia**, dan belum ada klaim apa pun
atasnya: keterbacaan tiga lampu yang kini lebih terang, jarak simetris, tinggi sentuh 48dp,
rasa reversal morph pada ~25/50/90% dan lima ketukan cepat (λ=40 masih hipotesis sejak
2026-10-27), konten panel yang tidak re-wrap saat shell membesar, hilangnya pill saat layar
mati, dan copotnya overlay saat ALIVE dibuka dari pill. Panel dev (7 ketukan pada wordmark)
hanya ada di build debug, jadi state kritis/limit tidak bisa dipaksa di build rilis ini —
hanya lewat pemakaian nyata.

Pengganti bukti visual yang **juga bukan pengganti perangkat**: hierarki view produksi
(`InfoPillService.build()` / `buildPanel()` / `TrafficLightDot.onDraw()`) dirender lewat
Robolectric native graphics pada 411dp/xxhdpi ke `app/build/living-render/*.png` dan diperiksa
sebagai gambar: compact normal/approaching/limit dan expanded normal/approaching/limit.
Yang terbukti dari itu: warna, glow, jarak, hierarki panel, dan pose karakter. Yang **tidak**
terbukti: frame rate morph, interaksi dengan status bar/notch fisik, dan perilaku window
overlay sungguhan di One UI.

## 12. Risiko tersisa

1. **Morph reversal masih hipotesis.** λ=40 dan cap 900 ms dipilih secara matematis dan
   teruji unit, tapi belum ada mata manusia yang menilai rasanya di perangkat.
2. **Pill bisa "belum bisa dibaca" setelah midnight sampai ada pembacaan otoritatif.**
   Snapshot menolak hari yang belum di-rollover. Selama guard aktif, tick guard melakukan
   rollover dan pill pulih dalam ≤60 detik. **Kalau accessibility service mati dan ALIVE
   tidak dibuka**, tidak ada yang melakukan rollover, jadi pill bisa tanpa angka sepanjang
   hari itu. Sebelum perubahan ini yang melakukan rollover justru pill — side effect yang
   kita hapus. Pemilik yang benar adalah penjadwal (alarm), bukan pembaca pertama; itu
   perubahan state enforcement dan butuh keputusan dia.
3. **Risiko read otoritatif basi membuka latch** (bagian 3) tidak disentuh.
4. **`onActivityStarted` menutup pill untuk semua Activity in-process.** Saat ini benar
   (MainActivity, LockActivity, GuardianAlertActivity); sebuah activity transparan di masa
   depan akan ikut menutup pill.
5. **Uji visual berbasis Robolectric**, bukan frame perangkat; `PillLightsTest` mengukur pada
   density 3 (xxhdpi) dengan pita pengukuran dalam dp, jadi kepadatan lain belum diukur.
6. **20 error lint lama** masih ada dan membuat `:app:lintDebug` gagal.

## 13. Pekerjaan yang sengaja tidak dilakukan

- **Tidak ada commit, push, publish, atau perubahan versi** (aturan 8 dan 3.9).
- **Tidak memasang APK sebelum dia mencolokkan HP dan meminta** — itu mengubah app yang sedang
  dia pakai. Setelah diminta, build dipasang dengan `-r` (20:56) dan dibuktikan lewat md5.
- **Tidak mengubah `EgoAccessibilityService`, aturan blokir, atau mekanisme lock.**
  Perubahan enforcement besar/berisiko tidak diterapkan secara spekulatif (A5).
- **Tidak menulis ulang `PillMorphEngine`/`PillMorphChoreography`.** Arsitektur satu-clock
  yang ada sudah benar dan terbukti; yang diperbaiki hanya kepemilikan lebar konten.
- **Tidak menambah aset, sprite, dependensi, atau statistik baru** untuk mengisi ruang.
- **Tidak mengubah copy panel** ("Entertainment today", "Most used", dsb.) — refinement
  mempertahankan teks; perubahan klaim butuh keputusan dia.
- **Tidak memperbaiki 20 error lint lama** (translation/theme/permission) — di luar cakupan
  dan menyentuh file yang tidak berkaitan.
- **Tidak menyentuh arsip history lintas hari** karena butuh perubahan pada jalur penulisan
  yang dipakai keputusan hari.

## 14. Rekomendasi langkah berikutnya

1. **Mata manusia pada morph.** Build ini sudah terpasang di HP-nya (20:56), jadi tinggal
   dijalankan — checklist di §11. Kalau λ=40 terasa salah, satu knob
   (`SPRING_RATE_PER_SECOND`) yang diubah. Emulator cuma cadangan; boot dari shell ini tidak
   bertahan.
2. **Putuskan nasib 20 error lint** sebelum rilis berikutnya: tambah translation, naikkan
   `minSdk`/guard untuk atribut theme, dan bungkus tiga `notify()` dengan izin yang eksplisit.
3. **Kalau angka pill hilang setelah midnight, itu bukan bug baru** — itu risiko §12 no. 2;
   yang perlu diputuskan adalah siapa pemilik rollover harian (alarm, bukan pembaca pertama).
4. Kalau mau melanjutkan hardening: tutup risiko #3 (read otoritatif basi) dengan
   seq-token pada jalur guard, dan putuskan apakah pill perlu menampilkan label yang lebih
   awam daripada "Entertainment today" (inisiatif klarifikasi).
