package com.botcatat.notiflogger

/**
 * URL Web App Google Apps Script -- ditanam langsung di source code, tidak
 * pernah diisi manual oleh pengguna aplikasi. Ganti nilai ini kalau kamu
 * redeploy Apps Script sebagai deployment baru (URL berbeda); untuk update
 * kode Apps Script biasa (versi baru dari deployment yang sama), URL ini
 * tidak berubah.
 */
object Config {
    const val WEB_APP_URL = "https://script.google.com/macros/s/AKfycbwmaD_puo3nZPzU5sfC84raKvut2bEkJSXviUO-PVyXY4g02fJ-7gBrRy2VFU6MOUN7mA/exec"

    /** Halaman panel yang dibuka dari tombol "Buka Panel" -- tampilan lebih rapi di GitHub Pages, tapi tetap memanggil WEB_APP_URL di baliknya. */
    const val PANEL_URL = "https://gabfx09.github.io/Autocatat/"
}
