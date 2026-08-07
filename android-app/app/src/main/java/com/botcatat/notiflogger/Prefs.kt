package com.botcatat.notiflogger

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

/** Wrapper kecil untuk menyimpan peta aplikasi->kategori bank/e-wallet yang dipantau. */
object Prefs {
    private const val FILE = "notiflogger_prefs"
    private const val KEY_MONITORED_MAP = "monitored_map"

    private fun sp(context: Context): SharedPreferences =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Peta packageName aplikasi terpasang -> kode kategori bank/e-wallet (mis. "BCA"). */
    fun getMonitoredMap(context: Context): Map<String, String> {
        val raw = sp(context).getString(KEY_MONITORED_MAP, null) ?: return emptyMap()
        return try {
            val json = JSONObject(raw)
            json.keys().asSequence().associateWith { key -> json.getString(key) }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun setMonitoredMap(context: Context, map: Map<String, String>) {
        val json = JSONObject()
        map.forEach { (pkg, category) -> json.put(pkg, category) }
        sp(context).edit().putString(KEY_MONITORED_MAP, json.toString()).apply()
    }
}
