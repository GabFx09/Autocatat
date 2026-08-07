# NotifLogger — Catat Notifikasi Bank/E-wallet ke Google Sheets

Aplikasi Android yang membaca notifikasi masuk dari aplikasi bank/e-wallet yang
kamu pilih, lalu otomatis mengirim datanya (nominal, jenis transaksi, isi
notifikasi) sebagai baris baru di Google Sheets.

Arsitektur:

```
[Notifikasi bank/e-wallet] --> [NotifLogger (Android)] --> HTTPS POST --> [Google Apps Script Web App] --> [Google Sheet]
```

Kenapa lewat Apps Script, bukan Google Sheets API langsung dari HP? Karena
kalau langsung, HP perlu login OAuth Google yang jauh lebih ribet diatur &
di-refresh tokennya. Web App Apps Script cukup dipanggil dengan URL + secret
key, dan tetap aman karena hanya bisa diakses lewat URL rahasia itu.

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
- `apps-script/Code.gs` — kode backend Google Apps Script

## 1. Deploy backend Google Apps Script (lakukan ini duluan)

1. Buka https://sheets.google.com, buat Spreadsheet baru, beri nama misalnya
   "Catatan Transaksi".
2. Di menu Sheet: **Extensions > Apps Script**.
3. Hapus isi default `Code.gs`, lalu tempel isi file `apps-script/Code.gs`
   dari project ini.
4. Ganti baris berikut dengan kunci rahasiamu sendiri (bebas, contoh string
   acak yang panjang):
   ```js
   var MY_SECRET = 'GANTI_DENGAN_KUNCI_RAHASIA_ANDA';
   ```
5. Di toolbar Apps Script, pilih fungsi `setup` dari dropdown, lalu klik
   **Run**. Saat diminta izin, klik **Review permissions** → pilih akun
   Google-mu → **Allow**. Ini akan membuat sheet "Transaksi" dengan header
   kolom dan menyimpan secret key di server.
6. Klik **Deploy > New deployment**.
   - Klik ikon gear, pilih tipe **Web app**.
   - Execute as: **Me**
   - Who has access: **Anyone**
   - Klik **Deploy**, izinkan akses lagi bila diminta.
7. Salin **Web app URL** yang muncul (formatnya
   `https://script.google.com/macros/s/xxxxx/exec`). URL ini yang nanti
   dimasukkan ke aplikasi Android.

> Catatan: setiap kali kamu mengubah `Code.gs`, kamu perlu **Deploy > Manage
> deployments > edit (pensil) > Version: New version > Deploy** agar
> perubahan aktif di URL yang sama.

## 2. Build & install aplikasi Android

Butuh **Android Studio** (mesin ini tidak punya Android SDK, jadi build harus
dilakukan di komputer/laptop yang sudah terpasang Android Studio).

1. Buka Android Studio → **Open** → pilih folder `android-app/`.
2. Tunggu Gradle sync selesai (Android Studio akan otomatis mengunduh Gradle
   wrapper & dependency yang dibutuhkan; jika muncul prompt "Gradle wrapper
   file is missing", klik OK untuk membiarkan Android Studio membuatkannya).
3. Sambungkan HP Android via USB (aktifkan **USB debugging** di Developer
   Options), atau pakai emulator.
4. Klik **Run ▶** untuk install aplikasi ke HP.

Kalau tidak punya Android Studio dan hanya ingin APK jadi, beri tahu saya —
saya bisa bantu siapkan build lewat GitHub Actions/cloud CI karena mesin ini
sendiri tidak bisa mem-build APK (tidak ada Android SDK terpasang).

## 3. Konfigurasi aplikasi di HP

Setelah aplikasi NotifLogger terpasang dan dibuka:

1. **URL Web App** — tempel URL dari langkah 1.7 di atas.
2. **Secret Key** — isi persis sama dengan `MY_SECRET` di `Code.gs`.
3. Klik **Simpan Pengaturan**.
4. Klik **Pilih Aplikasi**, centang aplikasi bank/e-wallet yang mau dipantau
   (BCA, Livin by Mandiri, BRImo, GoPay, OVO, DANA, ShopeePay, dll — daftar
   yang muncul otomatis dari aplikasi yang sudah terpasang di HP-mu).
5. Klik **Buka Pengaturan Akses Notifikasi**, cari "NotifLogger" di daftar,
   lalu aktifkan. Ini wajib — tanpa izin ini aplikasi tidak bisa membaca
   notifikasi apa pun.
6. Kembali ke aplikasi, pastikan status menunjukkan "akses notifikasi AKTIF".

Setelah itu, setiap kali ada notifikasi masuk dari aplikasi yang kamu pilih,
NotifLogger otomatis mengirimkannya ke Google Sheets — baris baru berisi
waktu, nama aplikasi, judul & isi notifikasi, nominal (jika terdeteksi), dan
jenis transaksi (Masuk/Keluar, best-effort dari kata kunci).

## Catatan keamanan & keterbatasan

- **Izin akses notifikasi bersifat luas** — begitu diaktifkan, Android
  memberi aplikasi akses baca ke SEMUA notifikasi di HP. NotifLogger hanya
  memproses & mengirim notifikasi dari paket aplikasi yang kamu centang di
  langkah 3.4, tapi izin OS-nya sendiri tidak bisa dibatasi per-app oleh
  aplikasi pihak ketiga manapun.
- **URL + secret key Apps Script setara password** — jangan dibagikan ke
  orang lain, karena siapa pun yang punya keduanya bisa menulis baris palsu
  ke sheet-mu.
- **Parsing nominal & jenis transaksi berbasis kata kunci**, bukan parser
  resmi dari bank, jadi bisa meleset untuk format notifikasi yang tidak
  terduga. Judul & isi notifikasi asli tetap disimpan apa adanya di kolom
  terpisah supaya data mentah tidak hilang meski parsing salah.
- Jika HP dalam mode hemat baterai agresif (Doze/App Standby), pengiriman
  bisa tertunda beberapa saat — WorkManager akan tetap mencoba ulang begitu
  ada koneksi internet.
