/**
 * Everything the site says, in the two languages ALIVE speaks.
 *
 * Indonesian first: it is the product's own voice and the reason it exists.
 * English is a translation of that voice, not a second copy written beside it -
 * when a line changes in one, the other has to change too, which is what the
 * shared shape enforces at compile time.
 *
 * The beats are the old site's seven panels, kept in order. The claims inside
 * them are the app's: what ALIVE counts, what it locks, what it never uploads.
 */

const id = {
  meta: {
    brand: 'ALIVE',
    tagline: 'Sadari waktumu.',
    skip: 'Lewati ke konten',
    toTop: 'Kembali ke atas'
  },

  nav: {
    download: 'Unduh APK',
    langLabel: 'Bahasa',
    story: 'Cerita'
  },

  hero: {
    badge: '01 — keberadaan',
    title: ['SADARI', 'WAKTUMU.'],
    lead: 'HP-mu bukan musuhnya. Hari-hari yang hilang tanpa kamu sadari — itu dia.',
    body:
      'ALIVE menghitung waktu hiburan di layarmu, memberi kamu jatah harian, dan menutup app-nya sendiri saat jatah habis. Bukan untuk menghukum. Supaya yang memutuskan masih kamu, di saat kamu masih mau memutuskan.',
    cta: 'Unduh ALIVE',
    ctaNote: 'APK beta · ±5 MB · Android 8 ke atas',
    scroll: 'gulir',
    face: 'Karakter ALIVE melambai'
  },

  loop: {
    badge: '02 — looping',
    title: ['SCROLL', 'TANPA SADAR.'],
    lead: 'Tiga hal ini yang bikin satu malam habis tanpa sisa.',
    items: [
      {
        name: 'Kelelahan dopamin',
        body:
          'Stimulasi mikro terus-menerus menguras jalur reward di otak. Motivasi untuk usaha yang nyata ikut kering.'
      },
      {
        name: 'Atensi yang pecah',
        body:
          'Otak menyesuaikan diri ke rangsangan 15 detik. Fokus dalam, memori, dan daya tahan berpikir tergerus.'
      },
      {
        name: 'Cemas kronis pasif',
        body:
          'Banjir judul sensasional menaikkan stres tanpa kamu sadari, dan merusak kualitas tidur.'
      }
    ],
    note:
      'Tiga hal di atas bukan temuan ALIVE — itu hasil penelitian yang sudah ada. Kami cuma bikin alat yang membuat angka-angka itu kelihatan di HP sendiri.'
  },

  awareness: {
    badge: '03 — sadar',
    title: '3 JAM 33 MENIT',
    lead: 'bukan statistik. satu malam.',
    body:
      'Angka ini keluar dari salah satu hari pemakaian nyata. ALIVE tidak menasihatimu — dia cuma menghitung, lalu menunjukkannya apa adanya.',
    meter: {
      label: 'Hari ini',
      allowance: '152% dari jatah',
      screen: '4j58 layar',
      apps: [
        { name: 'Chrome', minutes: '1 jam 33 menit' },
        { name: 'TikTok', minutes: '1 jam 12 menit' },
        { name: 'WhatsApp', minutes: '43 menit' }
      ]
    },
    facts: [
      { name: 'Jatah hiburan', body: 'Pagi kamu tentuin batasnya. Malam dia yang ingat.' },
      { name: 'Time Bank', body: 'Hari tanpa hiburan menabung menit buat hari yang berat.' },
      { name: 'Beruntun', body: 'Hari yang kamu jaga dihitung sebagai satu rantai, bukan satu kemenangan.' }
    ]
  },

  realization: {
    badge: '04 — realisasi',
    title: ['BERAPA BANYAK', 'HIDUPMU?'],
    lead: 'Waktu hiburan bukan waktu luang. Itu jam yang nggak balik.',
    body:
      'Sekali kamu lihat angkanya, kamu nggak bisa pura-pura tidak tahu. Dari situ pilihan mulai ada.',
    privacy: {
      title: 'Semua yang ALIVE tahu tetap di sini.',
      body:
        'Nggak ada akun. Nggak ada server. Nggak ada yang meng-upload harimu. Yang dihitung hanya app yang benar-benar ALIVE tutup — chat, kerja, dan app yang kamu pakai buat fokus nggak pernah memotong jatah.',
      marks: ['Tanpa akun', 'Tanpa sinkronisasi', 'Tanpa iklan']
    }
  },

  takeover: {
    alt: 'Poster pixel: HP raksasa bermuka monster dengan tangan panjang menjulur ke karakter pink kecil di bawahnya',
    line: 'Yang pegang kendali sekarang bukan kamu.',
    note: 'Ini yang ALIVE lihat setiap hari. Bukan buat nakut-nakutin — supaya kelihatan.'
  },

  choice: {
    badge: '06 — pilihan',
    title: ['INI BUKAN', 'SALAH HP-MU.'],
    lead: 'Kamu nggak lemah, dan layarnya nggak jahat.',
    body:
      'Yang hilang cuma satu: batas yang kamu tetapkan sendiri, di saat kamu masih mau menetapkannya. ALIVE menyimpan batas itu buat kamu, lalu menjaganya saat kamu sudah tidak sanggup.',
    steps: [
      {
        name: '30 menit sebelum habis',
        body: 'Satu peringatan tenang, muncul di dalam app yang lagi kamu buka. Bukan notifikasi yang bisa di-swipe.'
      },
      {
        name: 'Jatah habis',
        body: 'App hiburannya tertutup sendiri. Telepon, chat, dan app kerja tetap terbuka — itu bukan yang sedang kamu hindari.'
      },
      {
        name: 'Hari pemulihan',
        body: 'Sehari tanpa hiburan tetap dihitung. Kamu nggak dihukum karena pernah kelepasan; rantai boleh dimulai lagi.'
      }
    ]
  },

  guardian: {
    badge: '07 — penjaga',
    title: ['JANGAN BIARKAN', 'GUARDIAN KELUAR'],
    lead: 'Selama kamu jaga janjimu sendiri, kamu nggak akan pernah lihat dia.',
    body:
      'Orang-orang yang memakai ALIVE punya satu cerita yang sama. Kalau harimu lepas dari batas yang kamu tetapkan sendiri, ada yang datang. Nggak marah, nggak menghukum — dia cuma berdiri di situ sampai kamu kembali.',
    places: [
      {
        name: 'Tanda pertama',
        body: 'Sebuah notifikasi yang datang dan nggak mau pergi.'
      },
      {
        name: 'Tanda kedua',
        body: 'Satu kartu di halaman utama, dengan satu tombol untuk kembali ke jalur.'
      },
      {
        name: 'Tanda ketiga',
        body: 'Sosok yang berdiri sebentar di tengah layar, di atas apa pun yang sedang kamu buka.'
      }
    ],
    honest:
      'Tidak ada yang tahu persis kapan dia datang. Yang pasti: dia datang kalau kamu melanggar janjimu sendiri.',
    alt: 'Sosok pixel berjubah hitam bertanduk, bermata amber, dengan tongkat berlampu'
  },

  room: {
    badge: '08 — ruang',
    title: ['SISAKAN', 'RUANG.'],
    lead: 'Menutup satu pintu bukan tujuan. Membuka satu jam iya.',
    body:
      'ALIVE sekalian memberi tempat buat jam yang tersisa, supaya “berhenti” tidak berakhir jadi melamun di dinding.',
    features: [
      {
        name: 'Sesi fokus',
        body: '30, 60, 90, atau 120 menit dengan daftar app yang boleh dipakai. Keluar dari daftar itu, ALIVE tegur.'
      },
      {
        name: 'Misi harian',
        body: 'Membaca, jalan kaki, beres-beres, olahraga — selesai atau tidak dinilai dari bukti yang kamu kasih, bukan dari perasaan.'
      },
      {
        name: 'Statistik',
        body: 'Sehari dipotong blok tiga jam, per app, dan total layar — termasuk yang tidak memotong jatah.'
      },
      {
        name: 'Widget',
        body: 'Angka hari itu ada di home screen, jadi kamu lihat sebelum memutuskan buka app-nya.'
      }
    ]
  },

  download: {
    badge: '09 — selesai',
    title: ['NIKMATI', 'HIDUPMU.'],
    lead: 'Satu unduhan, tanpa akun.',
    file: {
      name: 'alive.apk',
      size: '±5 MB',
      requires: 'Android 8.0 (API 26) ke atas'
    },
    cta: 'Unduh APK',
    honest:
      'ALIVE disebar sebagai APK beta lewat GitHub Releases: Android akan menanyakan izin “sumber tidak dikenal” sebelum memasang, dan belum ada pembaruan otomatis — tapi tombol ini selalu menunjuk rilis terbaru. Versi Play Store sedang disiapkan.',
    permissions: {
      title: 'Dua izin yang diminta',
      usage: {
        name: 'Data penggunaan',
        body: 'Buat menghitung berapa lama app hiburan ada di layar. Dibaca lokal, tidak pernah dikirim.'
      },
      access: {
        name: 'Aksesibilitas',
        body: 'Buat menutup app hiburan saat jatah habis. Ini juga yang membuat ALIVE bisa melihat app mana yang sedang terbuka — karena itu dia jujur soal datanya di atas.'
      }
    }
  },

  footer: {
    line: 'ALIVE',
    sub: 'Sadari waktumu, kendalikan layarmu.',
    promise: 'Tanpa akun · tanpa server · tanpa izin internet.',
    legal: '© 2026 proyek independen soal atensi',
    links: 'Tautan',
    privacy: 'Kebijakan privasi',
    contact: 'Kontak'
  }
}

/** The same sentences, in English. `typeof id` is the contract. */
const en: typeof id = {
  meta: {
    brand: 'ALIVE',
    tagline: 'Know your time.',
    skip: 'Skip to content',
    toTop: 'Back to top'
  },

  nav: {
    download: 'Download APK',
    langLabel: 'Language',
    story: 'Story'
  },

  hero: {
    badge: '01 — existence',
    title: ['KNOW', 'YOUR TIME.'],
    lead: 'Your phone is not the enemy. The days that went missing without you noticing are.',
    body:
      'ALIVE counts the entertainment time on your screen, gives you a daily allowance, and closes those apps itself when it runs out. Not to punish you — so that the one deciding is still you, while deciding is still something you want to do.',
    cta: 'Download ALIVE',
    ctaNote: 'Beta APK · ~5 MB · Android 8 and up',
    scroll: 'scroll',
    face: 'The ALIVE character waving'
  },

  loop: {
    badge: '02 — the loop',
    title: ['SCROLLING', 'UNCONSCIOUS.'],
    lead: 'These three are what leave one evening with nothing to show for it.',
    items: [
      {
        name: 'Dopamine exhaustion',
        body:
          'Constant micro-stimulation drains the brain’s reward pathways, and motivation for real effort dries up with them.'
      },
      {
        name: 'Attention fragmentation',
        body:
          'The brain adapts to 15-second stimuli. Deep focus, memory and cognitive endurance erode.'
      },
      {
        name: 'Passive chronic anxiety',
        body:
          'A flood of sensational headlines raises stress below the threshold of noticing, and sleep pays for it.'
      }
    ],
    note:
      'Those three are not ALIVE’s findings — they are published research. ALIVE is only the tool that makes the numbers show up on your own phone.'
  },

  awareness: {
    badge: '03 — awareness',
    title: '3 H 33 MIN',
    lead: 'not a statistic. one evening.',
    body:
      'That figure came out of one real day of use. ALIVE does not lecture you — it counts, then shows you what it counted.',
    meter: {
      label: 'Today',
      allowance: '152% of the allowance',
      screen: '4h58 screen',
      apps: [
        { name: 'Chrome', minutes: '1 h 33 min' },
        { name: 'TikTok', minutes: '1 h 12 min' },
        { name: 'WhatsApp', minutes: '43 min' }
      ]
    },
    facts: [
      { name: 'Entertainment allowance', body: 'You set the limit in the morning. It remembers for the evening.' },
      { name: 'Time Bank', body: 'A day without entertainment saves minutes for a day that needs them.' },
      { name: 'Streak', body: 'A day you held counts as part of a chain, not as one win.' }
    ]
  },

  realization: {
    badge: '04 — realization',
    title: ['HOW MUCH', 'OF YOUR LIFE?'],
    lead: 'Entertainment time is not free time. It is hours that do not come back.',
    body:
      'Once you have seen the number you cannot unsee it. That is where a choice starts to exist.',
    privacy: {
      title: 'Everything ALIVE knows stays here.',
      body:
        'No account. No server. Nothing about your day is uploaded. Only the apps ALIVE actually closes spend your allowance — chat, work and the apps you focus with are never charged.',
      marks: ['No account', 'No sync', 'No ads']
    }
  },

  takeover: {
    alt: 'Pixel poster: a giant phone with a monster face and long hands reaching down at a small pink character',
    line: 'The one holding control right now is not you.',
    note: 'This is what ALIVE sees every day. Not to frighten you — so that it can be seen.'
  },

  choice: {
    badge: '06 — choice',
    title: ['IT WAS NEVER', 'THE PHONE’S FAULT.'],
    lead: 'You are not weak, and the screen is not evil.',
    body:
      'The only thing missing was a limit you set yourself, at a moment when you still wanted to set one. ALIVE keeps that limit for you and holds it when you no longer can.',
    steps: [
      {
        name: '30 minutes before',
        body: 'One quiet warning, inside the app you are already in. Not a notification you can swipe away.'
      },
      {
        name: 'Allowance spent',
        body: 'The entertainment apps close themselves. Phone, chat and work apps stay open — those are not what you are avoiding.'
      },
      {
        name: 'Recovery day',
        body: 'A day without entertainment still counts. You are not punished for slipping; the chain can start again.'
      }
    ]
  },

  guardian: {
    badge: '07 — the guardian',
    title: ['DON’T LET THE', 'GUARDIAN OUT'],
    lead: 'As long as you keep your own promise, you will never see it.',
    body:
      'Everyone who uses ALIVE tells the same story. When your day slips past the limit you set for yourself, something arrives. Not angry, not punishing — it simply stands there until you come back.',
    places: [
      {
        name: 'The first sign',
        body: 'A notification that arrives and will not leave.'
      },
      {
        name: 'The second sign',
        body: 'A card on the home screen, with one button to get back on the line.'
      },
      {
        name: 'The third sign',
        body: 'A figure that stands in the middle of the screen for a moment, over whatever you have open.'
      }
    ],
    honest:
      'Nobody knows exactly when it comes. What is certain: it comes when you break your own promise.',
    alt: 'A pixel figure in a black horned hood with amber eyes, holding a lit staff'
  },

  room: {
    badge: '08 — make room',
    title: ['LEAVE', 'ROOM.'],
    lead: 'Closing one door was never the point. Opening an hour is.',
    body:
      'ALIVE also gives the hours it saves somewhere to go, so that “stop” does not end with staring at a wall.',
    features: [
      {
        name: 'Focus sessions',
        body: '30, 60, 90 or 120 minutes with a list of apps the session allows. Leave that list and ALIVE says so.'
      },
      {
        name: 'Daily missions',
        body: 'Reading, walking, tidying, exercise — done or not is judged by the evidence you give, not by how it felt.'
      },
      {
        name: 'Statistics',
        body: 'A day cut into three-hour blocks, per app, and total screen time — including what never touched the allowance.'
      },
      {
        name: 'Home widget',
        body: 'The day’s number sits on your home screen, so you see it before you decide to open the app.'
      }
    ]
  },

  download: {
    badge: '09 — peace',
    title: ['ENJOY', 'YOUR LIFE.'],
    lead: 'One download, no account.',
    file: {
      name: 'alive.apk',
      size: '~5 MB',
      requires: 'Android 8.0 (API 26) and up'
    },
    cta: 'Download APK',
    honest:
      'ALIVE ships as a beta APK through GitHub Releases: Android will ask about unknown sources before it installs, and there is no automatic update yet — but this button always points at the newest release. The Play Store version is on the way.',
    permissions: {
      title: 'The two permissions it asks for',
      usage: {
        name: 'Usage access',
        body: 'To count how long an entertainment app is on screen. Read locally, never sent anywhere.'
      },
      access: {
        name: 'Accessibility',
        body: 'To close entertainment apps when the allowance is gone. It is also how ALIVE can see which app is open — which is why it says plainly, above, what it does with that.'
      }
    }
  },

  footer: {
    line: 'ALIVE',
    sub: 'Know your time. Control your screen.',
    promise: 'No account · no server · no internet permission.',
    legal: '© 2026 an independent project about attention',
    links: 'Links',
    privacy: 'Privacy policy',
    contact: 'Contact'
  }
}

export const dictionaries = { id, en } as const

export type Lang = keyof typeof dictionaries
export type Copy = typeof id

export const langs: { code: Lang; label: string }[] = [
  { code: 'id', label: 'ID' },
  { code: 'en', label: 'EN' }
]
