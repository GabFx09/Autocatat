package com.botcatat.notiflogger

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.botcatat.notiflogger.databinding.ActivityBankConfigBinding
import com.botcatat.notiflogger.databinding.ItemBankConfigBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BankConfigActivity : AppCompatActivity() {

    private data class TargetApp(val label: String, val key: String, val keywords: List<String>)

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

    private lateinit var binding: ActivityBankConfigBinding
    private val cardBindings = mutableMapOf<String, ItemBankConfigBinding>()
    private var spreadsheets: List<Api.SpreadsheetItem> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBankConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)

        buildCards()
        loadData()

        binding.btnSave.setOnClickListener { saveAll() }
    }

    private fun resolveInstalledPackage(keywords: List<String>): String? {
        val pm = packageManager
        return pm.getInstalledApplications(0)
            .firstOrNull { app ->
                pm.getLaunchIntentForPackage(app.packageName) != null &&
                    keywords.any { kw -> pm.getApplicationLabel(app).toString().lowercase().contains(kw) }
            }
            ?.packageName
    }

    private fun buildCards() {
        val monitoredMap = Prefs.getMonitoredMap(this)
        val inflater = LayoutInflater.from(this)

        targetApps.forEach { target ->
            val item = ItemBankConfigBinding.inflate(inflater, binding.containerCards, false)
            val installedPackage = resolveInstalledPackage(target.keywords)

            item.textLabel.text = target.label
            item.switchEnabled.tag = installedPackage

            if (installedPackage == null) {
                item.switchEnabled.isEnabled = false
                item.textNotInstalled.text = "Tidak terpasang di HP ini"
            } else {
                item.switchEnabled.isChecked = monitoredMap.containsKey(installedPackage)
                item.textNotInstalled.text = ""
            }

            binding.containerCards.addView(item.root)
            cardBindings[target.key] = item
        }
    }

    private fun loadData() {
        binding.btnSave.isEnabled = false
        lifecycleScope.launch {
            val fetchedSpreadsheets = withContext(Dispatchers.IO) { Api.listSpreadsheets() }
            val status = withContext(Dispatchers.IO) { Api.fetchStatus() }
            spreadsheets = fetchedSpreadsheets
            binding.btnSave.isEnabled = true

            val spreadsheetOptions = listOf("-- Pilih Spreadsheet --") + spreadsheets.map { it.name }
            val spreadsheetAdapter = ArrayAdapter(this@BankConfigActivity, android.R.layout.simple_spinner_dropdown_item, spreadsheetOptions)

            targetApps.forEach { target ->
                val item = cardBindings[target.key] ?: return@forEach
                item.spinnerSpreadsheet.adapter = spreadsheetAdapter

                val current = status[target.key]
                val selectedIndex = spreadsheets.indexOfFirst { it.id == current?.spreadsheetId }
                if (selectedIndex >= 0) {
                    item.spinnerSpreadsheet.setSelection(selectedIndex + 1)
                    loadSheetsForCard(target.key, spreadsheets[selectedIndex].id, current?.sheetName)
                }

                item.spinnerSpreadsheet.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                        if (position <= 0) {
                            item.spinnerSheet.adapter = ArrayAdapter(
                                this@BankConfigActivity, android.R.layout.simple_spinner_dropdown_item, listOf("-- Pilih Sheet --")
                            )
                            return
                        }
                        loadSheetsForCard(target.key, spreadsheets[position - 1].id, null)
                    }

                    override fun onNothingSelected(parent: AdapterView<*>?) {}
                }
            }
        }
    }

    private fun loadSheetsForCard(key: String, spreadsheetId: String, preselectSheet: String?) {
        val item = cardBindings[key] ?: return
        lifecycleScope.launch {
            val sheetNames = withContext(Dispatchers.IO) { Api.listSheets(spreadsheetId) }
            val options = listOf("-- Pilih Sheet --") + sheetNames
            item.spinnerSheet.adapter = ArrayAdapter(this@BankConfigActivity, android.R.layout.simple_spinner_dropdown_item, options)
            val idx = sheetNames.indexOf(preselectSheet)
            if (idx >= 0) item.spinnerSheet.setSelection(idx + 1)
        }
    }

    private fun saveAll() {
        val monitoredMap = mutableMapOf<String, String>()
        val configs = mutableMapOf<String, Pair<String, String>>()

        targetApps.forEach { target ->
            val item = cardBindings[target.key] ?: return@forEach
            val packageName = item.switchEnabled.tag as String?

            if (item.switchEnabled.isChecked && packageName != null) {
                monitoredMap[packageName] = target.key
            }

            val spreadsheetIndex = item.spinnerSpreadsheet.selectedItemPosition
            val sheetIndex = item.spinnerSheet.selectedItemPosition
            if (spreadsheetIndex > 0 && sheetIndex > 0) {
                val spreadsheetId = spreadsheets[spreadsheetIndex - 1].id
                val sheetName = item.spinnerSheet.selectedItem as String
                configs[target.key] = spreadsheetId to sheetName
            }
        }

        Prefs.setMonitoredMap(this, monitoredMap)

        binding.btnSave.isEnabled = false
        lifecycleScope.launch {
            val success = withContext(Dispatchers.IO) { Api.saveConfig(configs) }
            binding.btnSave.isEnabled = true
            Toast.makeText(
                this@BankConfigActivity,
                if (success) "Tersimpan" else "Pilihan aplikasi tersimpan, tapi gagal mengirim Sheet tujuan ke server (coba lagi)",
                Toast.LENGTH_LONG
            ).show()
            if (success) finish()
        }
    }
}
