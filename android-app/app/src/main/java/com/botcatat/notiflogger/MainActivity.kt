package com.botcatat.notiflogger

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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

        binding.btnNotifAccess.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
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
