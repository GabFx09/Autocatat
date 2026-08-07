package com.botcatat.notiflogger

import android.content.Context
import android.content.SharedPreferences

/** Wrapper kecil untuk menyimpan pengaturan: URL Apps Script, secret key, dan paket aplikasi yang dipantau. */
object Prefs {
    private const val FILE = "notiflogger_prefs"
    private const val KEY_URL = "webapp_url"
    private const val KEY_SECRET = "secret_key"
    private const val KEY_PACKAGES = "monitored_packages"

    private fun sp(context: Context): SharedPreferences =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun getWebAppUrl(context: Context): String = sp(context).getString(KEY_URL, "") ?: ""

    fun setWebAppUrl(context: Context, url: String) {
        sp(context).edit().putString(KEY_URL, url).apply()
    }

    fun getSecretKey(context: Context): String = sp(context).getString(KEY_SECRET, "") ?: ""

    fun setSecretKey(context: Context, secret: String) {
        sp(context).edit().putString(KEY_SECRET, secret).apply()
    }

    fun getMonitoredPackages(context: Context): Set<String> =
        sp(context).getStringSet(KEY_PACKAGES, DEFAULT_PACKAGES) ?: DEFAULT_PACKAGES

    fun setMonitoredPackages(context: Context, packages: Set<String>) {
        sp(context).edit().putStringSet(KEY_PACKAGES, packages).apply()
    }

    // Tebakan awal paket bank/e-wallet populer di Indonesia. Pengguna sebaiknya
    // tetap mengecek & menyesuaikan lewat "Pilih Aplikasi" di MainActivity karena
    // nama paket bisa berubah antar versi app.
    val DEFAULT_PACKAGES: Set<String> = setOf(
        "com.bca.mybca",
        "id.co.bri.brimo",
        "com.bnimobilebanking.digital",
        "id.co.bankmandiri.livin",
        "com.bankmandiri.livinmerchant",
        "com.jenius.bankbtpn",
        "com.cimbniaga.octoclicks",
        "com.gojek.app",
        "com.gojek.gopay",
        "id.dana",
        "com.dana",
        "com.shopee.pay",
        "com.telkomsel.tcash",
        "com.linkaja",
        "ovo.id"
    )
}
