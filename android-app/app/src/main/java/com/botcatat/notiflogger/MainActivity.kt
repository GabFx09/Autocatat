package com.botcatat.notiflogger

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.botcatat.notiflogger.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {

    private data class TargetApp(val label: String, val key: String, val keywords: List<String>)

    private data class ConnectionInfo(val docName: String, val sheetName: String, val connected: Boolean)

    private val targetApps = listOf(
        TargetApp("BCA", "BCA", listOf("bca")),
        TargetApp("BRI", "BRI", listOf("bri")),
        TargetApp("Mandiri", "MANDIRI", listOf("mandiri", "livin")),
        TargetApp("BNI", "BNI", listOf("bni")),
        TargetApp("DANA", "DANA", listOf("dana")),
        TargetApp("OVO", "OVO", listOf("ovo")),
        TargetApp("LinkAja", "LINKAJA", listOf("linkaja", "link aja")),
        TargetApp("GoPay", "GOPAY", listOf("gojek", "gopay"))
    )

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        refreshSelectedAppsLabel()

        binding.btnPickApps.setOnClickListener { showAppPicker() }

        binding.btnOpenPanel.setOnClickListener {
            if (!Config.WEB_APP_URL.startsWith("https://")) {
                Toast.makeText(this, "Aplikasi belum di-build dengan URL panel yang valid", Toast.LENGTH_SHORT).show()
            } else {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Config.WEB_APP_URL)))
            }
        }

        binding.btnNotifAccess.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
    }

    override fun onResume() {
        super.onResume()
        binding.textStatus.text = if (isNotificationAccessGranted()) {
            "Status: akses notifikasi AKTIF"
        } else {
            "Status: akses notifikasi BELUM diaktifkan"
        }
    }

    private fun isNotificationAccessGranted(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return enabled != null && enabled.contains(packageName)
    }

    private fun refreshSelectedAppsLabel() {
        val selected = Prefs.getMonitoredMap(this)
        binding.textSelectedApps.text = if (selected.isEmpty()) {
            "Belum dipilih"
        } else {
            selected.values.sorted().joinToString(", ")
        }
    }

    /** Cari paket aplikasi yang terpasang di HP yang label-nya cocok salah satu kata kunci target. */
    private fun resolveInstalledPackage(keywords: List<String>): String? {
        val pm = packageManager
        return pm.getInstalledApplications(0)
            .firstOrNull { app ->
                pm.getLaunchIntentForPackage(app.packageName) != null &&
                    keywords.any { kw -> pm.getApplicationLabel(app).toString().lowercase().contains(kw) }
            }
            ?.packageName
    }

    private fun fetchConnectionStatus(): Map<String, ConnectionInfo> {
        if (!Config.WEB_APP_URL.startsWith("https://")) return emptyMap()
        return try {
            val url = URL("${Config.WEB_APP_URL}?format=json")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 10000
            }
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()
            val json = JSONObject(text)
            targetApps.associate { target ->
                val obj = json.optJSONObject(target.key)
                target.key to ConnectionInfo(
                    docName = obj?.optString("docName").orEmpty(),
                    sheetName = obj?.optString("sheetName").orEmpty(),
                    connected = obj?.optBoolean("connected") ?: false
                )
            }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun showAppPicker() {
        binding.btnPickApps.isEnabled = false
        lifecycleScope.launch {
            val status = withContext(Dispatchers.IO) { fetchConnectionStatus() }
            binding.btnPickApps.isEnabled = true
            showAppPickerDialog(status)
        }
    }

    private fun showAppPickerDialog(status: Map<String, ConnectionInfo>) {
        val resolvedPackages = targetApps.map { resolveInstalledPackage(it.keywords) }
        val currentMap = Prefs.getMonitoredMap(this)

        val labels = targetApps.mapIndexed { i, target ->
            val info = status[target.key]
            val sheetPart = if (info != null && info.connected) {
                " — ${info.docName} > ${info.sheetName}"
            } else {
                " — (belum terhubung)"
            }
            val installedPart = if (resolvedPackages[i] == null) " (tidak terpasang)" else ""
            "${target.label}$sheetPart$installedPart"
        }.toTypedArray()
        val checkedItems = resolvedPackages.map { it != null && currentMap.containsKey(it) }.toBooleanArray()

        val tempSelection = currentMap.toMutableMap()

        val dialog = AlertDialog.Builder(this)
            .setTitle("Pilih aplikasi bank/e-wallet")
            .setMultiChoiceItems(labels, checkedItems) { dialogInterface, which, isChecked ->
                val pkg = resolvedPackages[which]
                if (pkg == null) {
                    (dialogInterface as AlertDialog).listView.setItemChecked(which, false)
                    Toast.makeText(this, "${targetApps[which].label} tidak terpasang di HP ini", Toast.LENGTH_SHORT).show()
                    return@setMultiChoiceItems
                }
                if (isChecked) tempSelection[pkg] = targetApps[which].key else tempSelection.remove(pkg)
            }
            .setPositiveButton("Simpan") { _, _ ->
                Prefs.setMonitoredMap(this, tempSelection)
                refreshSelectedAppsLabel()
            }
            .setNegativeButton("Batal", null)
            .create()
        dialog.show()
    }
}
