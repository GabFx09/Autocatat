package com.botcatat.notiflogger

import android.content.Intent
import android.net.Uri
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

        refreshSelectedAppsLabel()

        binding.btnPickApps.setOnClickListener {
            startActivity(Intent(this, BankConfigActivity::class.java))
        }

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
        refreshSelectedAppsLabel()
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
}
