# NotifLogger — Catat Notifikasi Bank/E-wallet ke Google Sheets

Aplikasi Android yang membaca notifikasi masuk dari 8 aplikasi bank/e-wallet
(BCA, BRI, Mandiri, BNI, DANA, OVO, LinkAja, GoPay), lalu otomatis mengirim
datanya (nominal, jenis transaksi, isi notifikasi) sebagai baris baru ke
Google Sheets — **masing-masing bank/e-wallet bisa punya Google Sheet & tab
tujuannya sendiri-sendiri**, diatur lewat satu Panel admin di browser
komputer.

Arsitektur:

```
[Notifikasi bank/e-wallet] --> [NotifLogger (Android)] --> HTTPS POST --> [Google Apps Script Web App] --> [Google Sheet sesuai kategori]
                                                                    ^
                                                          [Panel admin di komputer]
                                                       (buka URL Web App di browser,
                                                        atur Sheet & tab per bank)
```

URL Web App **ditanam langsung di source code aplikasi** (`Config.kt`) saat
build — pengguna aplikasi di HP tidak pernah perlu mengisi atau melihat URL
apa pun. Ke Google Sheet & tab mana data tiap bank/e-wallet dicatat diatur
sepenuhnya dari Panel admin (dibuka lewat browser komputer dengan URL yang
sama). Jadi kalau kamu ganti/pindah spreadsheet tujuan, cukup ubah di Panel
— semua HP yang sudah terpasang otomatis ikut, tanpa update aplikasi maupun
setting apa pun di HP.

## Download APK tanpa Android Studio

Setiap ada perubahan kode yang di-push ke branch `main`, GitHub Actions
otomatis membuild APK dan menerbitkannya di halaman **Releases** repo ini
(`.github/workflows/build-apk.yml`). Untuk dapat APK terbaru:

1. Buka tab **Releases** di halaman GitHub repo ini
2. Download file `app-debug.apk` dari release paling atas (paling baru)
3. Pindahkan ke HP, install seperti biasa (aktifkan "Install dari sumber
   tidak dikenal" bila diminta)

Cara ini bisa dipakai untuk update ke banyak HP sekaligus tanpa perlu USB
atau Android Studio di HP manapun — cukup download APK-nya dan install manual
di tiap HP.

## Struktur folder

- `android-app/` — project Android Studio (Kotlin)
  - `Config.kt` — berisi URL Web App yang ditanam saat build
- `apps-script/Code.gs` — kode backend Google Apps Script + Panel admin

## 1. Deploy backend Google Apps Script (lakukan ini duluan)

1. Buka https://script.google.com, klik **New project** (atau lewat
   Extensions > Apps Script dari sebuah Google Sheet mana pun).
2. Hapus isi default `Code.gs`, lalu tempel isi file `apps-script/Code.gs`
   dari project ini.
3. Klik **Deploy > New deployment**.
   - Klik ikon gear, pilih tipe **Web app**.
   - Execute as: **Me**
   - Who has access: **Anyone** (bukan "Anyone with Google account" — kalau
     salah pilih ini, aplikasi akan diminta login Google dan gagal)
   - Klik **Deploy**, izinkan akses (Review permissions → pilih akun
     Google-mu → Allow) bila diminta.
4. Salin **Web app URL** yang muncul (formatnya
   `https://script.google.com/macros/s/xxxxx/exec`). URL ini **selamanya
   tidak berubah** walau kamu ganti-ganti spreadsheet tujuan.
5. Tempel URL itu ke `android-app/app/src/main/java/com/botcatat/notiflogger/Config.kt`,
   ganti nilai `WEB_APP_URL`, lalu commit & push — GitHub Actions akan
   otomatis build ulang APK dengan URL itu tertanam di dalamnya.

> Catatan: setiap kali kamu mengubah isi `Code.gs` di kemudian hari, perlu
> **Deploy > Manage deployments > edit (pensil) > Version: New version >
> Deploy** agar perubahan aktif di URL yang sama (tidak perlu ubah
> `Config.kt` lagi kalau URL-nya tidak berubah).
>
> Catatan izin: backend sekarang membaca daftar Spreadsheet dari Google
> Drive-mu untuk ditampilkan sebagai dropdown, jadi saat deploy/redeploy
> Google mungkin minta izin akses Drive yang lebih luas — klik **Allow**
> seperti biasa.

## 2. Atur Panel admin — hubungkan tiap bank/e-wallet ke Spreadsheet & Sheet-nya

Panel ini bisa dibuka dari **browser komputer** (via URL Web App langsung)
maupun dari **aplikasi Android** (tombol "Pilih Aplikasi") — keduanya
memakai dropdown yang sama, tidak perlu paste link sama sekali.

1. Buka URL Web App dari langkah 1.4 di atas **di browser komputer** — akan
   muncul halaman **Panel NotifLogger** dengan 8 bagian: BCA, BRI, Mandiri,
   BNI, DANA, OVO, LinkAja, GoPay.
2. Untuk tiap bank/e-wallet yang mau dicatat, pilih dari dropdown:
   - **Spreadsheet** — daftar semua Google Sheet yang ada di Drive akun ini
   - **Sheet/Tab** — daftar tab di dalam Spreadsheet yang baru dipilih
     (muncul otomatis setelah Spreadsheet dipilih; kalau tabnya belum ada,
     akan dibuat otomatis beserta header kolom saat data pertama masuk)
3. Klik **Simpan Semua**.

Kapan pun kamu mau pindah Spreadsheet/Sheet tujuan salah satu bank (atau
semuanya), tinggal buka lagi Panel ini dan pilih ulang dari dropdown —
tidak perlu sentuh source code maupun HP sama sekali.

## 3. Build & install aplikasi Android

Butuh **Android Studio**.

1. Buka Android Studio → **Open** → pilih folder `android-app/`.
2. Tunggu Gradle sync selesai.
3. Sambungkan HP Android via USB (aktifkan **USB debugging** di Developer
   Options), atau pakai emulator.
4. Klik **Run ▶** untuk install aplikasi ke HP.

Atau tanpa Android Studio sama sekali, lihat bagian **Download APK tanpa
Android Studio** di atas.

## 4. Konfigurasi aplikasi di HP

Setelah aplikasi NotifLogger terpasang dan dibuka — tidak ada URL yang perlu
diisi sama sekali:

1. Klik **Pilih Aplikasi**. Akan terbuka layar dengan 8 kartu (BCA, BRI,
   Mandiri, BNI, DANA, OVO, LinkAja, GoPay), masing-masing berisi:
   - **Toggle ON/OFF** — aktifkan untuk bank/e-wallet yang mau dipantau
     (nonaktif otomatis kalau aplikasinya tidak terpasang di HP ini)
   - **Dropdown Spreadsheet** — pilih Google Sheet tujuan dari daftar
   - **Dropdown Sheet/Tab** — pilih tab tujuan (muncul otomatis setelah
     Spreadsheet dipilih)
2. Atur semua yang perlu, klik **Simpan** di bagian bawah layar — ini
   menyimpan pilihan aplikasi ke HP dan Spreadsheet/Sheet tujuan ke server
   sekaligus.
3. Kembali ke layar utama, klik **Buka Pengaturan Akses Notifikasi**, cari
   "NotifLogger" di daftar, lalu aktifkan. Ini wajib — tanpa izin ini
   aplikasi tidak bisa membaca notifikasi apa pun.
4. Pastikan status menunjukkan "akses notifikasi AKTIF".

(Ada juga tombol **"Buka Panel (Atur Sheet Tujuan)"** di layar utama sebagai
jalan pintas membuka Panel yang sama di browser HP — berguna kalau mau atur
tanpa app terbuka, misalnya dibagikan ke orang lain yang memegang HP tapi
tidak install app-nya.)

Setelah itu, setiap kali ada notifikasi masuk dari salah satu dari 8 aplikasi
yang kamu aktifkan, NotifLogger otomatis mengirimkannya ke Spreadsheet &
Sheet yang sudah dipilih untuk bank/e-wallet itu. Kolom yang ditulis:

| Kolom | Isi |
|---|---|
| A | Tanggal |
| B | Kode (BCA/BRI/MANDIRI/BNI/DANA/OVO/LINKAJA/GOPAY) |
| C | Waktu notifikasi diterima HP |
| D | Waktu diproses server |
| E | Judul notifikasi |
| F | Isi notifikasi |
| G | Nominal (angka murni, tanpa "Rp"/titik) |

Baris otomatis diberi warna latar pada kolom E:G — **hijau** kalau jenis
transaksinya "Masuk", **oranye** kalau "Keluar" (dideteksi best-effort dari
kata kunci di isi notifikasi).

## Catatan keamanan & keterbatasan

- **Repo ini Public dan tidak ada secret key** — URL Web App tertanam di
  source code (`Config.kt`) yang bisa dilihat siapa saja, dan juga bisa
  diekstrak dari APK yang didownload publik. Konsekuensinya: siapa pun yang
  menemukan URL ini secara teknis bisa mengirim baris data palsu ke
  Sheet-mu, dan endpoint `?format=json&action=list_spreadsheets` membocorkan
  **nama semua Google Sheet yang ada di Drive akun ini** (bukan isi
  datanya) supaya bisa ditampilkan sebagai dropdown. Kalau nama-nama
  dokumen Google Drive-mu ada yang sensitif, pertimbangkan untuk membuat
  repo Private (konsekuensi: download APK dari Releases perlu login GitHub)
  — ini trade-off yang disengaja demi kemudahan (tidak perlu isi/paste apa
  pun secara manual).
- **Izin akses notifikasi bersifat luas** — begitu diaktifkan, Android
  memberi aplikasi akses baca ke SEMUA notifikasi di HP. NotifLogger hanya
  memproses & mengirim notifikasi dari salah satu dari 8 aplikasi yang kamu
  centang, tapi izin OS-nya sendiri tidak bisa dibatasi per-app oleh
  aplikasi pihak ketiga manapun.
- **Parsing nominal & jenis transaksi berbasis kata kunci**, bukan parser
  resmi dari bank, jadi bisa meleset untuk format notifikasi yang tidak
  terduga. Judul & isi notifikasi asli tetap disimpan apa adanya di kolom
  terpisah supaya data mentah tidak hilang meski parsing salah.
- Jika HP dalam mode hemat baterai agresif (Doze/App Standby), pengiriman
  bisa tertunda beberapa saat — WorkManager akan tetap mencoba ulang begitu
  ada koneksi internet.
