package com.botcatat.notiflogger

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.ListenableWorker.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Mengirim satu notifikasi transaksi ke Google Apps Script Web App, dengan retry otomatis dari WorkManager. */
class UploadWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_CATEGORY = "category"
        const val KEY_APP_NAME = "app_name"
        const val KEY_APP_PACKAGE = "app_package"
        const val KEY_TITLE = "title"
        const val KEY_TEXT = "text"
        const val KEY_AMOUNT = "amount"
        const val KEY_TYPE = "type"
        const val KEY_TIMESTAMP = "timestamp"
        private const val TAG = "UploadWorker"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).apply {
            timeZone = TimeZone.getDefault()
        }
        val timestampMillis = inputData.getLong(KEY_TIMESTAMP, System.currentTimeMillis())

        val payload = JSONObject().apply {
            put("category", inputData.getString(KEY_CATEGORY) ?: "")
            put("timestamp", isoFormat.format(Date(timestampMillis)))
            put("appName", inputData.getString(KEY_APP_NAME) ?: "")
            put("appPackage", inputData.getString(KEY_APP_PACKAGE) ?: "")
            put("title", inputData.getString(KEY_TITLE) ?: "")
            put("text", inputData.getString(KEY_TEXT) ?: "")
            put("amount", inputData.getString(KEY_AMOUNT) ?: "")
            put("type", inputData.getString(KEY_TYPE) ?: "")
        }

        var connection: HttpURLConnection? = null
        try {
            val url = URL(Config.WEB_APP_URL)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 15000
                readTimeout = 15000
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                instanceFollowRedirects = true
            }

            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(payload.toString())
            }

            val code = connection.responseCode
            if (code in 200..299) {
                Result.success()
            } else {
                Log.w(TAG, "Upload gagal, kode HTTP $code")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Upload error: ${e.message}")
            Result.retry()
        } finally {
            connection?.disconnect()
        }
    }
}
