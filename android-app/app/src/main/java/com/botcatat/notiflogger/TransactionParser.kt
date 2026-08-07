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

    private val AMOUNT_PATTERN: Pattern = Pattern.compile(
        "Rp\\.?\\s?([\\d]{1,3}(?:[.,]\\d{3})*(?:[.,]\\d{2})?)",
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
        val matcher = AMOUNT_PATTERN.matcher(text)
        val amount = if (matcher.find()) "Rp${matcher.group(1)}" else null

        val lower = text.lowercase()
        val type = when {
            MASUK_KEYWORDS.any { lower.contains(it) } -> "Masuk"
            KELUAR_KEYWORDS.any { lower.contains(it) } -> "Keluar"
            else -> null
        }

        return ParsedTransaction(amount, type)
    }
}
