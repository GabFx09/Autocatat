package com.botcatat.notiflogger

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.getSystemService
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

class NotificationLoggerService : NotificationListenerService() {

    companion object {
        private const val CHANNEL_ID = "notiflogger_running"
        private const val FOREGROUND_ID = 1
    }

    /**
     * Naikkan service ini jadi foreground service begitu sistem menyambungkan
     * listener notifikasi, supaya Android (dan kebanyakan battery manager
     * pabrikan) jauh lebih kecil kemungkinan mematikannya paksa di latar
     * belakang. Konsekuensinya: selalu ada 1 notifikasi permanen berprioritas
     * rendah selama aplikasi aktif.
     */
    override fun onListenerConnected() {
        super.onListenerConnected()

        val manager = getSystemService<NotificationManager>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "NotifLogger Aktif",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Menandakan NotifLogger sedang memantau notifikasi di latar belakang"
                setShowBadge(false)
            }
            manager?.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("NotifLogger aktif")
            .setContentText("Memantau notifikasi bank/e-wallet di latar belakang")
            .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()

        ServiceCompat.startForeground(
            this,
            FOREGROUND_ID,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            }
        )
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)

        val packageName = sbn.packageName
        val monitoredMap = Prefs.getMonitoredMap(applicationContext)
        val category = monitoredMap[packageName] ?: return

        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString() ?: ""

        if (title.isBlank() && text.isBlank()) return

        val appName = try {
            val pm = applicationContext.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        } catch (e: Exception) {
            packageName
        }

        val parsed = TransactionParser.parse("$title $text")

        if (parsed.type != "Masuk") return // hanya uang masuk yang dicatat, selain itu diabaikan
        if (!Config.WEB_APP_URL.startsWith("https://")) return // belum di-build dengan URL yang valid

        val inputData = Data.Builder()
            .putString(UploadWorker.KEY_CATEGORY, category)
            .putString(UploadWorker.KEY_APP_NAME, appName)
            .putString(UploadWorker.KEY_APP_PACKAGE, packageName)
            .putString(UploadWorker.KEY_TITLE, title)
            .putString(UploadWorker.KEY_TEXT, text)
            .putString(UploadWorker.KEY_AMOUNT, parsed.amount ?: "")
            .putString(UploadWorker.KEY_TYPE, parsed.type)
            .putLong(UploadWorker.KEY_TIMESTAMP, sbn.postTime)
            .build()

        val request = OneTimeWorkRequestBuilder<UploadWorker>()
            .setInputData(inputData)
            .build()

        WorkManager.getInstance(applicationContext).enqueue(request)
    }
}
