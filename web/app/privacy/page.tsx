import type { Metadata } from 'next'
import { basePath, brand, contactEmail } from '@/lib/site'

export const metadata: Metadata = {
  title: 'Kebijakan Privasi',
  description:
    'ALIVE tidak punya server dan tidak meminta izin internet. Ini halaman yang menjelaskan apa yang ada di HP-mu, izin apa yang diminta dan untuk apa, serta bagaimana menghapusnya.',
  alternates: { canonical: '/privacy/' },
  openGraph: {
    title: 'Kebijakan Privasi — ALIVE',
    description:
      'Tanpa akun, tanpa server, tanpa izin internet. Apa yang disimpan ALIVE, dan bagaimana menghapusnya.',
    url: '/privacy/',
    type: 'website'
  }
}

/**
 * A server component on purpose. The app's own page is interactive and switches
 * language in the browser; this one is a document a Play reviewer and a crawler
 * both have to read as plain HTML, in both languages, with no script running.
 *
 * Every sentence here is a claim about the manifest, not about intentions: the
 * app declares no INTERNET permission, keeps seven private preference files, and
 * excludes its per-app usage history from Android's own backup.
 */

const effective = '11 Oktober 2026'
const effectiveEn = '11 October 2026'

function Block({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <section className="card-sm p-5 md:p-6">
      <h2 className="eyebrow">{label}</h2>
      <div className="mt-3 space-y-3 text-[15px] leading-relaxed">{children}</div>
    </section>
  )
}

export default function PrivacyPage() {
  return (
    <main className="mx-auto w-full max-w-3xl px-5 py-14 md:py-20">
      <a href={`${basePath}/`} className="text-sm text-ink-secondary underline">
        ← {brand}
      </a>

      <h1 className="mt-4 font-head text-3xl font-bold md:text-4xl">Kebijakan Privasi</h1>
      <p className="mt-2 text-ink-secondary">Berlaku sejak {effective}</p>

      <div className="mt-8 space-y-6">
        <Block label="Yang tidak terjadi">
          <p>
            ALIVE tidak punya akun, tidak punya server, dan tidak meminta izin internet. Aplikasi
            ini secara teknis tidak bisa mengirim apa pun keluar dari HP-mu — bukan karena kami
            berjanji tidak akan, tapi karena jalannya memang tidak ada.
          </p>
          <p>
            Tidak ada analytics, tidak ada iklan, tidak ada SDK pihak ketiga yang mengumpulkan
            data, dan tidak ada laporan crash yang dikirim otomatis.
          </p>
        </Block>

        <Block label="Yang ada di HP-mu">
          <p>Semua ini disimpan di folder privat aplikasi, tempat hanya ALIVE dan sistem yang bisa membaca:</p>
          <ul className="list-disc space-y-1 pl-5">
            <li>Perkiraan durasi pemakaian app hiburan per hari, dan total layar harian.</li>
            <li>Batas hiburan yang kamu tetapkan, sisa jatah, dan hari yang sudah dihitung.</li>
            <li>Sesi fokus, daftar app yang boleh dipakai, misi harian dan bukti yang kamu catat.</li>
            <li>Pengaturan tampilan dan status layar sambutan.</li>
          </ul>
          <p>
            Yang dihitung hanya app yang benar-benar ALIVE tutup atau awasi. Isi chat, isi layar,
            dan nama file-mu tidak dibaca sama sekali.
          </p>
        </Block>

        <Block label="Izin yang diminta, dan untuk apa">
          <ul className="list-disc space-y-2 pl-5">
            <li>
              <strong>Akses penggunaan</strong> — membaca durasi app di layar, supaya jatah harian
              bisa dihitung. Izin ini kamu berikan sendiri di pengaturan sistem dan bisa kamu cabut
              kapan pun.
            </li>
            <li>
              <strong>Aksesibilitas</strong> — menutup app hiburan saat jatah habis. Ini izin yang
              paling kuat di antara semuanya, karena dia membuat ALIVE tahu app mana yang sedang
              terbuka. Karena itu juga dia hanya aktif kalau kamu menyalakannya, dan dia tidak
              membaca isi layar.
            </li>
            <li>
              <strong>Tampil di atas app lain</strong> — dipakai hanya saat perlindungan sedang mati,
              untuk menaruh satu kartu pemulihan di atas app yang sedang kamu buka.
            </li>
            <li>
              <strong>Notifikasi</strong> — satu notifikasi saja, yang menagih perlindungan yang
              kamu matikan.
            </li>
            <li>
              <strong>Aktivitas fisik (penghitung langkah)</strong> — hanya untuk misi yang memang
              membuktikannya. Tidak dipakai untuk melacak lokasi, dan lokasi tidak diminta.
            </li>
          </ul>
        </Block>

        <Block label="Cadangan bawaan Android">
          <p>
            Kalau kamu menyalakan cadangan perangkat di sistem Android, pengaturan ALIVE bisa ikut
            tercadang ke akun Google-mu. Itu fitur sistem, bukan ALIVE yang meng-upload. Riwayat
            pemakaian per app sengaja dikecualikan dari cadangan itu, jadi angka harimu tidak ikut
            ke mana-mana. Kamu bisa mematikan cadangan kapan pun di pengaturan sistem.
          </p>
        </Block>

        <Block label="Menghapus semuanya">
          <p>
            Copot aplikasinya, atau buka Pengaturan → Aplikasi → ALIVE → Penyimpanan → Hapus data.
            Tidak ada salinan di server kami untuk dihapus, karena tidak ada server.
          </p>
        </Block>

        <Block label="Untuk siapa aplikasi ini">
          <p>
            ALIVE dibuat untuk orang dewasa yang mengatur layarnya sendiri, dan tidak ditargetkan
            untuk anak-anak.
          </p>
        </Block>

        <Block label="Perubahan dan pertanyaan">
          <p>
            Kalau halaman ini berubah, tanggal di atas ikut berubah. Pertanyaan soal privasi bisa
            dikirim ke{' '}
            <a href={`mailto:${contactEmail}`} className="underline">
              {contactEmail}
            </a>
            .
          </p>
        </Block>
      </div>

      <div className="rule my-12" />

      <h1 className="font-head text-3xl font-bold md:text-4xl">Privacy Policy</h1>
      <p className="mt-2 text-ink-secondary">Effective {effectiveEn}</p>

      <div className="mt-8 space-y-6">
        <Block label="What does not happen">
          <p>
            ALIVE has no account and no server, and it does not ask for internet permission. The
            app cannot send anything off your phone — not because we promise not to, but because
            there is no route to do it.
          </p>
          <p>
            No analytics, no advertising, no third-party SDK that collects data, and no crash
            report sent automatically.
          </p>
        </Block>

        <Block label="What is on your phone">
          <p>All of this sits in the app&apos;s private folder, where only ALIVE and the system can read it:</p>
          <ul className="list-disc space-y-1 pl-5">
            <li>Estimated entertainment app usage per day, and the daily screen total.</li>
            <li>The limit you set, the allowance left, and the days already counted.</li>
            <li>Focus sessions, the apps a session allows, daily missions and the evidence you log.</li>
            <li>Display settings and the state of the welcome screens.</li>
          </ul>
          <p>
            Only apps ALIVE actually closes or watches are counted. Chat contents, screen contents
            and your file names are never read.
          </p>
        </Block>

        <Block label="The permissions it asks for, and why">
          <ul className="list-disc space-y-2 pl-5">
            <li>
              <strong>Usage access</strong> — reads how long an app is on screen so the daily
              allowance can be counted. You grant it in system settings and can revoke it anytime.
            </li>
            <li>
              <strong>Accessibility</strong> — closes entertainment apps when the allowance is
              gone. It is the strongest permission here, because it lets ALIVE know which app is
              open. That is why it is only on when you switch it on, and why it never reads what is
              on the screen.
            </li>
            <li>
              <strong>Display over other apps</strong> — used only while protection is off, to put
              one recovery card over whatever you have open.
            </li>
            <li>
              <strong>Notifications</strong> — one notification, which asks for the protection you
              switched off.
            </li>
            <li>
              <strong>Physical activity (step counter)</strong> — only for missions that prove it.
              It is not used for location, and location is never requested.
            </li>
          </ul>
        </Block>

        <Block label="Android’s own backup">
          <p>
            If you have device backup on in Android settings, ALIVE&apos;s preferences may be backed
            up to your own Google account. That is a system feature, not ALIVE uploading anything.
            The per-app usage history is deliberately excluded from that backup, so your day&apos;s
            numbers do not travel. You can switch backup off anytime in system settings.
          </p>
        </Block>

        <Block label="Deleting everything">
          <p>
            Uninstall the app, or open Settings → Apps → ALIVE → Storage → Clear data. There is no
            copy on our servers to delete, because there are no servers.
          </p>
        </Block>

        <Block label="Who this is for">
          <p>ALIVE is built for adults managing their own screen, and is not directed at children.</p>
        </Block>

        <Block label="Changes and questions">
          <p>
            When this page changes, the date above changes with it. Privacy questions go to{' '}
            <a href={`mailto:${contactEmail}`} className="underline">
              {contactEmail}
            </a>
            .
          </p>
        </Block>
      </div>
    </main>
  )
}
