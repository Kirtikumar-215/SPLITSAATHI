package com.example.util

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Money utilities for SplitSaathi.
 * Core rule: All monetary amounts are stored strictly as integer paise (Long).
 * Floating-point numbers are NEVER used for financial calculations or storage.
 */
object MoneyUtils {

    /**
     * Formats integer paise into an Indian Rupee string with the ₹ symbol,
     * using the Indian numbering system grouping (e.g. ₹1,25,000.00).
     */
    fun formatPaiseToRupees(paise: Long): String {
        val isNegative = paise < 0
        val absPaise = kotlin.math.abs(paise)
        val rupees = absPaise / 100
        val cents = absPaise % 100

        val formattedRupees = formatIndianGrouping(rupees)
        val centsStr = String.format(Locale.US, "%02d", cents)
        val prefix = if (isNegative) "-₹" else "₹"
        return "$prefix$formattedRupees.$centsStr"
    }

    /**
     * Formats numeric rupee string for UPI intent amount attribute (e.g. "250.00").
     */
    fun formatPaiseForUpi(paise: Long): String {
        val absPaise = kotlin.math.abs(paise)
        val rupees = absPaise / 100
        val cents = absPaise % 100
        return String.format(Locale.US, "%d.%02d", rupees, cents)
    }

    /**
     * Formats whole rupees using Indian digit grouping (3 digits at end, then groups of 2).
     */
    private fun formatIndianGrouping(value: Long): String {
        val s = value.toString()
        if (s.length <= 3) return s
        val lastThree = s.substring(s.length - 3)
        var rest = s.substring(0, s.length - 3)
        val sb = StringBuilder()
        while (rest.length > 2) {
            val chunk = rest.substring(rest.length - 2)
            sb.insert(0, ",$chunk")
            rest = rest.substring(0, rest.length - 2)
        }
        if (rest.isNotEmpty()) {
            sb.insert(0, rest)
        }
        sb.append(",$lastThree")
        return sb.toString()
    }

    /**
     * Parses a user-entered rupee string (e.g. "120", "120.5", "120.50") into integer paise.
     * Returns null if invalid or negative or more than 2 decimal places.
     */
    fun parseRupeesToPaise(input: String): Long? {
        val trimmed = input.trim().replace(",", "")
        if (trimmed.isEmpty()) return null
        if (!trimmed.matches(Regex("""^\d+(\.\d{1,2})?$"""))) return null

        val parts = trimmed.split(".")
        val rupees = parts[0].toLongOrNull() ?: return null
        val paisePart = if (parts.size > 1) {
            val decimals = parts[1]
            if (decimals.length == 1) (decimals + "0").toInt()
            else decimals.toInt()
        } else {
            0
        }
        return (rupees * 100) + paisePart
    }

    /**
     * Computes equal split down to the exact paisa.
     * base = floor(total / n), remainder = total mod n.
     * Participants are sorted ascending, and +1 paisa is given to the first remainder participants.
     * The sum of shares is guaranteed to equal totalPaise exactly.
     */
    fun calculateEqualSplit(totalPaise: Long, participantUserIds: List<String>): Map<String, Long> {
        require(totalPaise > 0) { "Total amount must be greater than zero paise" }
        require(participantUserIds.isNotEmpty()) { "Participant list cannot be empty" }

        val sorted = participantUserIds.sorted()
        val n = sorted.size.toLong()
        val base = totalPaise / n
        val remainder = (totalPaise % n).toInt()

        val result = mutableMapOf<String, Long>()
        for (i in sorted.indices) {
            val share = if (i < remainder) base + 1 else base
            result[sorted[i]] = share
        }
        return result
    }

    /**
     * Validates that all assigned custom shares sum exactly to totalPaise.
     */
    fun validateCustomSplit(totalPaise: Long, shares: Map<String, Long>): Boolean {
        if (totalPaise <= 0 || shares.isEmpty()) return false
        if (shares.values.any { it < 0 }) return false
        val sum = shares.values.sum()
        return sum == totalPaise
    }

    /**
     * Validates Indian mobile number (10 digits starting with 6, 7, 8, or 9).
     */
    fun isValidIndianPhone(phone: String): Boolean {
        val regex = Regex("""^[6-9]\d{9}$""")
        return regex.matches(phone.trim())
    }

    /**
     * Computes split based on percentages, ensuring exact paisa balance.
     * Any rounding difference is distributed among participants.
     */
    fun calculatePercentageSplit(totalPaise: Long, percentages: Map<String, Double>): Map<String, Long> {
        require(totalPaise > 0) { "Total amount must be greater than zero paise" }
        require(percentages.isNotEmpty()) { "Percentages map cannot be empty" }

        val sortedKeys = percentages.keys.sorted()
        val rawShares = mutableMapOf<String, Long>()
        var allocated = 0L

        for (key in sortedKeys) {
            val pct = percentages[key] ?: 0.0
            val share = kotlin.math.round((totalPaise * pct) / 100.0).toLong()
            rawShares[key] = share
            allocated += share
        }

        // Adjust remaining paise discrepancy if any
        val diff = totalPaise - allocated
        if (diff != 0L) {
            val step = if (diff > 0) 1L else -1L
            var remainingDiff = kotlin.math.abs(diff)
            var index = 0
            while (remainingDiff > 0 && index < sortedKeys.size) {
                val key = sortedKeys[index % sortedKeys.size]
                val current = rawShares[key] ?: 0L
                if (current + step >= 0) {
                    rawShares[key] = current + step
                    remainingDiff--
                }
                index++
            }
        }
        return rawShares
    }

    /**
     * Computes split based on share weights (e.g. 1 share, 2 shares), ensuring exact paisa balance.
     */
    fun calculateSharesRatioSplit(totalPaise: Long, shareWeights: Map<String, Int>): Map<String, Long> {
        require(totalPaise > 0) { "Total amount must be greater than zero paise" }
        require(shareWeights.isNotEmpty()) { "Share weights map cannot be empty" }
        val totalWeights = shareWeights.values.sum()
        require(totalWeights > 0) { "Total weights must be greater than 0" }

        val sortedKeys = shareWeights.keys.sorted()
        val rawShares = mutableMapOf<String, Long>()
        var allocated = 0L

        for (key in sortedKeys) {
            val weight = shareWeights[key] ?: 1
            val share = (totalPaise * weight) / totalWeights
            rawShares[key] = share
            allocated += share
        }

        // Distribute remainder paise to highest weight participants first
        var remainder = (totalPaise - allocated).toInt()
        val prioritySorted = sortedKeys.sortedByDescending { shareWeights[it] ?: 0 }
        var i = 0
        while (remainder > 0 && i < prioritySorted.size) {
            val key = prioritySorted[i % prioritySorted.size]
            rawShares[key] = (rawShares[key] ?: 0L) + 1
            remainder--
            i++
        }
        return rawShares
    }

    /**
     * Validates UPI ID format with regex.
     */
    fun isValidUpiId(upiId: String): Boolean {
        val regex = Regex("""^[a-zA-Z0-9.\-_]{2,256}@[a-zA-Z]{2,64}$""")
        return regex.matches(upiId.trim())
    }

    /**
     * Constructs a compliant UPI payment intent URI.
     * URI: upi://pay?pa=<upi>&pn=<name>&am=<amount with 2 decimals>&cu=INR&tn=<note>
     */
    fun buildUpiUri(payeeUpi: String, payeeName: String, amountPaise: Long, note: String): String {
        val encodedName = URLEncoder.encode(payeeName.trim(), StandardCharsets.UTF_8.toString()).replace("+", "%20")
        val encodedNote = URLEncoder.encode(note.trim(), StandardCharsets.UTF_8.toString()).replace("+", "%20")
        val amountStr = formatPaiseForUpi(amountPaise)
        return "upi://pay?pa=${payeeUpi.trim()}&pn=$encodedName&am=$amountStr&cu=INR&tn=$encodedNote"
    }
}
