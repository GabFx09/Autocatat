package com.botcatat.notiflogger

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

class NotificationLoggerService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)

        val packageName = sbn.packageName
        val monitored = Prefs.getMonitoredPackages(applicationContext)
        if (!monitored.contains(packageName)) return

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

        val url = Prefs.getWebAppUrl(applicationContext)
        val secret = Prefs.getSecretKey(applicationContext)
        if (url.isBlank()) return // belum dikonfigurasi, jangan coba kirim

        val inputData = Data.Builder()
            .putString(UploadWorker.KEY_URL, url)
            .putString(UploadWorker.KEY_SECRET, secret)
            .putString(UploadWorker.KEY_APP_NAME, appName)
            .putString(UploadWorker.KEY_APP_PACKAGE, packageName)
            .putString(UploadWorker.KEY_TITLE, title)
            .putString(UploadWorker.KEY_TEXT, text)
            .putString(UploadWorker.KEY_AMOUNT, parsed.amount ?: "")
            .putString(UploadWorker.KEY_TYPE, parsed.type ?: "")
            .putLong(UploadWorker.KEY_TIMESTAMP, sbn.postTime)
            .build()

        val request = OneTimeWorkRequestBuilder<UploadWorker>()
            .setInputData(inputData)
            .build()

        WorkManager.getInstance(applicationContext).enqueue(request)
    }
}
