# ALIVE — Living Interface: Motion Final Report

Tanggal: 2026-10-30 · Branch `master` · Build perangkat: `d8ef57a3…` (release-signed, versionCode 12)
Status: **disetujui pemilik** ("nice bro itu dia, cocok ini gw approve"). Tidak ada perubahan perilaku lain setelah persetujuan ini.

---

## 1. Ringkasan

Buka pill kini **spring dari keadaan diam**, bukan ease:

| | Nilai |
|---|---|
| Model buka | step response spring, ω = 15 rad/s, ζ = 0,62 |
| Wind-up | 40–60 ms pertama nyaris diam (objek berat, bukan melompat) |
| Puncak | **1,077× target di ~264 ms** |
| Rebound kedua | ~0,7 % sekitar 550 ms (yang terbaca "jelly") |
| Settle penuh | ~704 ms, cap animator **740 ms** |
| Tutup | **tidak diubah**: ease Decelerate 1,8, 210 ms, monoton |
| Konten | panel 15–66 ms, karakter 60–170 ms, baris 150–350 ms, tombol 220–330 ms |

**Kenapa ini terasa benar (dan versi sebelumnya tidak):**
- Ease mulai dengan **kecepatan maksimum** di frame pertama → "muncul", bukan "membuka".
- Spring mulai dari **nol** dan harus dipercepat → bobot.
- E1/E3 hanya menambah overshoot kecil di ujung (2,8 %) dan settle 30 ms → tetap kaku.
- ζ 0,62 memberi pantulan kedua yang **terlihat**; ζ 0,75 tidak (rebound 0,14 %, di bawah ambang settle).

## 2. Perjalanan bukti (ringkas)

| Tahap | Temuan terukur | Keputusan |
|---|---|---|
| Baseline (P2) | 90 Hz; buka 272–342 ms; 2 frame >32 ms; satu relayout per frame | Acuan |
| E1 (overshoot output) | Puncak di **47 % durasi** (konstanta hidup di progress, bukan waktu) | Diperbaiki |
| E3 (retiming) | Puncak 70,5–72,6 % durasi; perangkat: puncak ~75 %, tinggi +4,2 %; settle hanya **30 ms** | Kurang |
| A+C (420 ms + settle + konten menyusul) | Model: puncak 57 %, settle 181 ms | Masih kaku |
| Spring launch (rate 16, ζ 0,75) | Mulai dari diam; overshoot 2,8 % | "Dikit lagi" |
| **Final (rate 15, ζ 0,62)** | Overshoot 7,7 %, rebound kedua 0,7 %, settle 704 ms | **Disetujui** |

Pengukuran perangkat terakhir yang bisa diandalkan (E3, CPU idle, 90 Hz): 13 transisi, **0 frame >32 ms**, max 24,5 ms, relayout ≈1/frame, dua reversal kontinu tanpa lompatan. Build final dinilai dari model + test + mata pemilik; belum ada trace perangkat untuk build ini.

## 3. Perubahan yang ikut dipatenkan (satu batch)

**Motion**
- `PillMorphEngine`: spring launch dari diam (rate, damping, ceiling sebagai parameter), `launchRun`; `springRun` untuk reversal tetap critically damped.
- `InfoPillService`: buka memakai `launchRun` (cap 740 ms), tutup tetap `easeRun` 210 ms; radius dikunci agar tidak melewati token.
- `PillMorphChoreography`: staging konten dibuat mendarat bersama shell, bukan jauh sebelumnya.

**Correctness & lifecycle (batch yang sama, sudah teruji sebelumnya)**
- Pill membaca `readTodayEntertainmentSnapshot()` — tidak pernah mereset hari, menulis lock, atau menulis history.
- `PillReportSchedule.complete()` hanya menerima token aktif; `dismiss()` tidak menyapu antrian handler.
- Layar mati / Activity ALIVE muncul → overlay dilepas seketika (receiver + lifecycle callback).
- Cache laporan ber-`dayKey`; tidak ada angka kemarin yang tampil sebagai hari ini.

**Bukaan pertama tidak lagi tersendat**
- Panel diukur sekali saat pill menganggur (`pill.warm`, 11 ms, 1,2 s setelah pill tiba), bukan pada tap pertama.
- Terukur di perangkat: `pill.expand` **2,75–4,68 ms** pada semua tap (sebelumnya tap pertama **76,9 ms**, sisanya 3,8–5,9 ms).

**Visual**
- Tiga lampu tetap penuh warna dengan halo 4 dp; shader di-cache (tanpa alokasi per denyut).
- Header 48 dp (target sentuh), jarak simetris 14 dp, panel maks 320 dp, tile karakter 64 dp.

## 4. Bukti

- **710 test, 0 gagal, 0 error, 0 skip** (`:app:test --rerun-tasks`), termasuk:
  - launch: mulai dari diam, puncak 1,06–1,11, mendarat tepat, reversal mewarisi posisi+kecepatan;
  - reversal/follow-through lama tidak berubah;
  - staging konten dalam domain waktu (bukan fraksi).
- Build `assembleDebug` + `assembleRelease` hijau; APK terpasang byte-identik `d8ef57a34195c7dfd8162ebcbc20b630`.
- Mutation check: konstanta lama (puncak di progress 0,90) membuat test domain-waktu merah di 47,3 % — persis temuan audit.

## 5. Batasan yang tetap ada

1. **Tutup belum punya bobot.** Masih ease 210 ms; kalau nanti terasa "berat buka, ringan tutup", itu eksperimen berikutnya, bukan kelalaian.
2. Warm-up panel berjalan 1,2 detik setelah pill tiba; kalau seseorang mengetuk lebih cepat dari itu, tap pertama masih membayar sebagian biaya dingin (terukur 11 ms, bukan 77 ms).
3. Trace perangkat untuk irama final belum diambil; angka puncak/settle berasal dari integrator yang sama + test. Yang terukur di perangkat adalah biaya setup (`pill.expand`) dan warm-up.
4. Stall `relayoutWindow` (pernah 50–84 ms) bersifat lingkungan; run bersih terakhir 0 frame >32 ms.
5. Refresh perangkat berpindah 90↔60 Hz antar sesi; semua perbandingan dipisah per rate.
6. Rekaman 30 fps tidak bisa menangkap puncak selebar 1–2 frame pada 90 Hz.

## 6. Rollback

- Spring → ease: kembalikan pemilihan engine di `animateWindow` ke `easeRun(EXPAND_MILLIS, 0f, 1f)` dan set `EXPAND_MILLIS` 260.
- Jelly → halus: `LAUNCH_DAMPING` 0,62 → 0,75; lebih berat: `LAUNCH_RATE_PER_SECOND` 15 → 13.
- Tidak ada state, data, atau migrasi yang terlibat.

## 7. Berikutnya (belum dikerjakan)

Kurva tutup dengan sedikit bobot dan settle pendek (~260–300 ms) — supaya "lepas" terasa disengaja, bukan sekadar cepat.
