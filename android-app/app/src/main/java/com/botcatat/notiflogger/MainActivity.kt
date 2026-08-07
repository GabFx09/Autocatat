package com.botcatat.notiflogger

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.botcatat.notiflogger.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

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

    private fun showAppPicker() {
        val pm = packageManager
        val installedApps = pm.getInstalledApplications(0)
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
            .sortedBy { pm.getApplicationLabel(it).toString().lowercase() }

        val labels = installedApps.map { "${pm.getApplicationLabel(it)}  (${it.packageName})" }.toTypedArray()
        val packageNames = installedApps.map { it.packageName }
        val currentSelection = Prefs.getMonitoredPackages(this)
        val checkedItems = packageNames.map { currentSelection.contains(it) }.toBooleanArray()

        val tempSelection = currentSelection.toMutableSet()

        AlertDialog.Builder(this)
            .setTitle("Pilih aplikasi bank/e-wallet")
            .setMultiChoiceItems(labels, checkedItems) { _, which, isChecked ->
                val pkg = packageNames[which]
                if (isChecked) tempSelection.add(pkg) else tempSelection.remove(pkg)
            }
            .setPositiveButton("Simpan") { _, _ ->
                Prefs.setMonitoredPackages(this, tempSelection)
                refreshSelectedAppsLabel()
            }
            .setNegativeButton("Batal", null)
            .show()
    }
}
