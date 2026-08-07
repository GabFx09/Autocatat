package com.botcatat.notiflogger

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.botcatat.notiflogger.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private data class TargetApp(val label: String, val keywords: List<String>)

    private val targetApps = listOf(
        TargetApp("BCA", listOf("bca")),
        TargetApp("BRI", listOf("bri")),
        TargetApp("Mandiri", listOf("mandiri", "livin")),
        TargetApp("BNI", listOf("bni")),
        TargetApp("DANA", listOf("dana")),
        TargetApp("OVO", listOf("ovo")),
        TargetApp("LinkAja", listOf("linkaja", "link aja")),
        TargetApp("GoPay", listOf("gojek", "gopay"))
    )

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.editUrl.setText(Prefs.getWebAppUrl(this))
        binding.editSecret.setText(Prefs.getSecretKey(this))
        refreshSelectedAppsLabel()

        binding.btnSave.setOnClickListener {
            Prefs.setWebAppUrl(this, binding.editUrl.text.toString().trim())
            Prefs.setSecretKey(this, binding.editSecret.text.toString().trim())
            Toast.makeText(this, "Pengaturan disimpan", Toast.LENGTH_SHORT).show()
        }

        binding.btnPickApps.setOnClickListener { showAppPicker() }

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
        val selected = Prefs.getMonitoredPackages(this)
        binding.textSelectedApps.text = if (selected.isEmpty()) {
            "Belum dipilih"
        } else {
            selected.joinToString(", ") { pkg -> appLabelFor(pkg) }
        }
    }

    private fun appLabelFor(packageName: String): String = try {
        val info = this.packageManager.getApplicationInfo(packageName, 0)
        this.packageManager.getApplicationLabel(info).toString()
    } catch (e: Exception) {
        packageName
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

    private fun showAppPicker() {
        val resolvedPackages = targetApps.map { resolveInstalledPackage(it.keywords) }
        val currentSelection = Prefs.getMonitoredPackages(this)

        val labels = targetApps.mapIndexed { i, target ->
            if (resolvedPackages[i] != null) target.label else "${target.label} (tidak terpasang)"
        }.toTypedArray()
        val checkedItems = resolvedPackages.map { it != null && currentSelection.contains(it) }.toBooleanArray()

        val tempSelection = currentSelection.toMutableSet()

        val dialog = AlertDialog.Builder(this)
            .setTitle("Pilih aplikasi bank/e-wallet")
            .setMultiChoiceItems(labels, checkedItems) { dialogInterface, which, isChecked ->
                val pkg = resolvedPackages[which]
                if (pkg == null) {
                    (dialogInterface as AlertDialog).listView.setItemChecked(which, false)
                    Toast.makeText(this, "${targetApps[which].label} tidak terpasang di HP ini", Toast.LENGTH_SHORT).show()
                    return@setMultiChoiceItems
                }
                if (isChecked) tempSelection.add(pkg) else tempSelection.remove(pkg)
            }
            .setPositiveButton("Simpan") { _, _ ->
                Prefs.setMonitoredPackages(this, tempSelection)
                refreshSelectedAppsLabel()
            }
            .setNegativeButton("Batal", null)
            .create()
        dialog.show()
    }
}
