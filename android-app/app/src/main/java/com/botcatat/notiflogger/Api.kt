package com.botcatat.notiflogger

import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/** Panggilan jaringan ke backend Apps Script. Semua fungsi di sini blocking -- panggil dari Dispatchers.IO. */
object Api {
    data class SpreadsheetItem(val id: String, val name: String)
    data class CategoryStatus(
        val spreadsheetId: String,
        val spreadsheetName: String,
        val sheetName: String,
        val connected: Boolean
    )

    private fun get(query: String): String? {
        if (!Config.WEB_APP_URL.startsWith("https://")) return null
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(Config.WEB_APP_URL + query).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = 15000
            }
            connection.inputStream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    fun listSpreadsheets(): List<SpreadsheetItem> {
        val text = get("?format=json&action=list_spreadsheets") ?: return emptyList()
        return try {
            val array = JSONArray(text)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                SpreadsheetItem(obj.getString("id"), obj.getString("name"))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun listSheets(spreadsheetId: String): List<String> {
        val text = get("?format=json&action=list_sheets&spreadsheetId=$spreadsheetId") ?: return emptyList()
        return try {
            val array = JSONArray(text)
            (0 until array.length()).map { array.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun fetchStatus(): Map<String, CategoryStatus> {
        val text = get("?format=json") ?: return emptyMap()
        return try {
            val obj = JSONObject(text)
            obj.keys().asSequence().associateWith { key ->
                val c = obj.getJSONObject(key)
                CategoryStatus(
                    spreadsheetId = c.optString("spreadsheetId"),
                    spreadsheetName = c.optString("spreadsheetName"),
                    sheetName = c.optString("sheetName"),
                    connected = c.optBoolean("connected")
                )
            }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun saveConfig(configs: Map<String, Pair<String, String>>): Boolean {
        if (!Config.WEB_APP_URL.startsWith("https://")) return false
        var connection: HttpURLConnection? = null
        return try {
            val configsJson = JSONObject()
            configs.forEach { (key, value) ->
                configsJson.put(key, JSONObject().apply {
                    put("spreadsheetId", value.first)
                    put("sheetName", value.second)
                })
            }
            val payload = JSONObject().apply {
                put("action", "save_config")
                put("configs", configsJson)
            }

            connection = (URL(Config.WEB_APP_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 15000
                readTimeout = 15000
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(payload.toString()) }

            connection.responseCode in 200..299
        } catch (e: Exception) {
            false
        } finally {
            connection?.disconnect()
        }
    }
}
