package com.botcatat.notiflogger

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.botcatat.notiflogger.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val notifPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        refreshSelectedAppsLabel()
        requestPostNotificationsPermissionIfNeeded()

        binding.btnPickApps.setOnClickListener {
            startActivity(Intent(this, BankConfigActivity::class.java))
        }

        binding.btnNotifAccess.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        binding.btnBatteryOptimization.setOnClickListener {
            requestIgnoreBatteryOptimizations()
        }

        binding.btnAutostart.setOnClickListener {
            openAutostartSettings()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshSelectedAppsLabel()

        if (isNotificationAccessGranted()) {
            binding.textStatus.text = "STATUS: AKSES NOTIFIKASI AKTIF"
            binding.textStatus.setTextColor(ContextCompat.getColor(this, R.color.success_sage))
        } else {
            binding.textStatus.text = "STATUS: BELUM DIAKTIFKAN"
            binding.textStatus.setTextColor(ContextCompat.getColor(this, R.color.error_brick))
        }

        if (isIgnoringBatteryOptimizations()) {
            binding.textBatteryStatus.text = "STATUS: OPTIMASI BATERAI SUDAH DIMATIKAN"
            binding.textBatteryStatus.setTextColor(ContextCompat.getColor(this, R.color.success_sage))
        } else {
            binding.textBatteryStatus.text = "STATUS: OPTIMASI BATERAI MASIH AKTIF"
            binding.textBatteryStatus.setTextColor(ContextCompat.getColor(this, R.color.error_brick))
        }
    }

    private fun isNotificationAccessGranted(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return enabled != null && enabled.contains(packageName)
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        val powerManager = getSystemService<PowerManager>() ?: return false
        return powerManager.isIgnoringBatteryOptimizations(packageName)
    }

    private fun requestPostNotificationsPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun requestIgnoreBatteryOptimizations() {
        if (isIgnoringBatteryOptimizations()) {
            Toast.makeText(this, "Sudah aktif", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName")
                )
            )
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    /**
     * Beberapa merk HP (Xiaomi/MIUI, Oppo, Vivo, dll.) punya battery manager
     * sendiri di luar API baterai standar Android, dengan halaman setelan
     * "Autostart"/"Protected Apps" yang tidak bisa diaktifkan lewat kode --
     * cuma bisa diarahkan ke halamannya lalu pengguna aktifkan manual.
     */
    private fun openAutostartSettings() {
        val candidates = listOf(
            Intent().setComponent(
                android.content.ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                )
            ),
            Intent().setComponent(
                android.content.ComponentName(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                )
            ),
            Intent().setComponent(
                android.content.ComponentName(
                    "com.oppo.safe",
                    "com.oppo.safe.permission.startup.StartupAppListActivity"
                )
            ),
            Intent().setComponent(
                android.content.ComponentName(
                    "com.iqoo.secure",
                    "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"
                )
            ),
            Intent().setComponent(
                android.content.ComponentName(
                    "com.vivo.permissionmanager",
                    "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                )
            ),
            Intent().setComponent(
                android.content.ComponentName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                )
            ),
            Intent().setComponent(
                android.content.ComponentName(
                    "com.samsung.android.lool",
                    "com.samsung.android.sm.ui.battery.BatteryActivity"
                )
            )
        )

        for (intent in candidates) {
            try {
                startActivity(intent)
                return
            } catch (e: Exception) {
                // Merk ini tidak cocok, coba kandidat berikutnya.
            }
        }

        try {
            startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:$packageName")
                )
            )
            Toast.makeText(
                this,
                "Buka menu Baterai di halaman ini dan aktifkan \"Tanpa batasan\"/\"Autostart\"",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Tidak ditemukan setelan khusus untuk HP ini", Toast.LENGTH_SHORT).show()
        }
    }

    private fun refreshSelectedAppsLabel() {
        val selected = Prefs.getMonitoredMap(this)
        binding.textSelectedApps.text = if (selected.isEmpty()) {
            "Belum dipilih"
        } else {
            selected.values.sorted().joinToString(", ")
        }
    }
}
