package com.botcatat.notiflogger

import java.util.regex.Pattern

data class ParsedTransaction(
    val amount: String?,
    val type: String?
)

/**
 * Parser berbasis kata kunci untuk teks notifikasi bank/e-wallet Indonesia.
 * Ini best-effort saja -- teks mentah notifikasi tetap dikirim apa adanya
 * sehingga tidak ada data yang hilang walau parsing gagal/salah.
 */
object TransactionParser {

    // Format "Rp1.234.000" -- titik/koma dianggap pemisah ribuan, tanpa desimal.
    private val RP_AMOUNT_PATTERN: Pattern = Pattern.compile(
        "Rp\\.?\\s?([\\d]{1,3}(?:[.,]\\d{3})*(?:[.,]\\d{2})?)",
        Pattern.CASE_INSENSITIVE
    )

    // Format "IDR 5,000.00" -- koma pemisah ribuan, titik desimal (sen dibuang).
    private val IDR_AMOUNT_PATTERN: Pattern = Pattern.compile(
        "IDR\\.?\\s?([\\d]{1,3}(?:,\\d{3})*(?:\\.\\d{2})?)",
        Pattern.CASE_INSENSITIVE
    )

    private val MASUK_KEYWORDS = listOf(
        "diterima", "menerima", "masuk", "kredit", "credit", "top up berhasil",
        "topup berhasil", "terima dana", "dana masuk", "pengembalian"
    )

    private val KELUAR_KEYWORDS = listOf(
        "debit", "debet", "pembayaran", "bayar", "beli", "transfer ke",
        "tarik tunai", "penarikan", "keluar", "belanja", "transaksi qris"
    )

    fun parse(text: String): ParsedTransaction {
        val amount = extractAmount(text)

        val lower = text.lowercase()
        val type = when {
            MASUK_KEYWORDS.any { lower.contains(it) } -> "Masuk"
            KELUAR_KEYWORDS.any { lower.contains(it) } -> "Keluar"
            else -> null
        }

        return ParsedTransaction(amount, type)
    }

    // Selalu kembalikan string berisi digit murni nominal (mis. "Rp5000"),
    // supaya sisi server (yang cuma membuang karakter non-digit) tidak
    // salah baca pemisah ribuan/desimal dari format sumber yang beda-beda.
    private fun extractAmount(text: String): String? {
        val rpMatcher = RP_AMOUNT_PATTERN.matcher(text)
        if (rpMatcher.find()) {
            val digits = rpMatcher.group(1).replace(Regex("[^0-9]"), "")
            return "Rp$digits"
        }

        val idrMatcher = IDR_AMOUNT_PATTERN.matcher(text)
        if (idrMatcher.find()) {
            val integerPart = idrMatcher.group(1).substringBefore(".").replace(",", "")
            return "Rp$integerPart"
        }

        return null
    }
}
