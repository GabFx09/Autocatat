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
        sp(context).getStringSet(KEY_PACKAGES, emptySet()) ?: emptySet()

    fun setMonitoredPackages(context: Context, packages: Set<String>) {
        sp(context).edit().putStringSet(KEY_PACKAGES, packages).apply()
    }
}
