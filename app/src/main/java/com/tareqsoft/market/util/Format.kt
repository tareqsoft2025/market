package com.tareqsoft.market.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Money is stored as Long minor units with 2 decimals (1250 = 12.50). */
object Money {
    fun format(minor: Long): String {
        val bd = BigDecimal.valueOf(minor).movePointLeft(2).setScale(2)
        return String.format(Locale.US, "%,.2f", bd)
    }

    fun format(minor: Double): String = format(Math.round(minor))

    /** Plain text for editing fields (no thousands separators). */
    fun toInput(minor: Long): String =
        BigDecimal.valueOf(minor).movePointLeft(2).setScale(2).stripTrailingZeros().toPlainString()

    /** Parses "12.5" or "12,5" into 1250. Returns null if invalid. */
    fun parse(text: String): Long? {
        val t = normalizeDigits(text).trim().replace(',', '.')
        if (t.isEmpty()) return null
        return try {
            BigDecimal(t).setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact()
        } catch (e: Exception) {
            null
        }
    }
}

object Qty {
    fun format(q: Double): String {
        val bd = BigDecimal.valueOf(q).setScale(3, RoundingMode.HALF_UP).stripTrailingZeros()
        return if (bd.scale() <= 0) bd.setScale(0).toPlainString() else bd.toPlainString()
    }

    /** Accepts "1.5", "1,5", Arabic digits and fractions such as "1/2" or "1 1/2". */
    fun parse(text: String): Double? {
        val t = normalizeDigits(text).trim().replace(',', '.').replace('\u2044', '/')
        if (t.isEmpty()) return null
        if ('/' in t) {
            val parts = t.split(' ').filter { it.isNotBlank() }
            val whole = if (parts.size == 2) parts[0].toDoubleOrNull() ?: return null else 0.0
            val frac = parts.last().split('/')
            if (frac.size != 2) return null
            val num = frac[0].toDoubleOrNull() ?: return null
            val den = frac[1].toDoubleOrNull() ?: return null
            if (den == 0.0) return null
            return whole + num / den
        }
        return t.toDoubleOrNull()
    }
}

/** Converts Arabic-Indic digits (٠-٩, ۰-۹) and Arabic decimal separator to ASCII. */
fun normalizeDigits(s: String): String {
    val sb = StringBuilder(s.length)
    for (c in s) {
        sb.append(
            when (c) {
                in '٠'..'٩' -> '0' + (c - '٠')
                in '۰'..'۹' -> '0' + (c - '۰')
                '٫' -> '.'
                '٬' -> ','
                else -> c
            }
        )
    }
    return sb.toString()
}

object Dates {
    private val dateFmt get() = SimpleDateFormat("yyyy/MM/dd", Locale.US)
    private val dateTimeFmt get() = SimpleDateFormat("yyyy/MM/dd  hh:mm a", Locale.US)
    private val timeFmt get() = SimpleDateFormat("hh:mm a", Locale.US)

    fun date(ts: Long): String = dateFmt.format(Date(ts))
    fun dateTime(ts: Long): String = dateTimeFmt.format(Date(ts))
    fun time(ts: Long): String = timeFmt.format(Date(ts))

    fun startOfDay(ts: Long): Long = Calendar.getInstance().apply {
        timeInMillis = ts
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun endOfDay(ts: Long): Long = addDays(startOfDay(ts), 1) - 1

    fun addDays(ts: Long, days: Int): Long = Calendar.getInstance().apply {
        timeInMillis = ts
        add(Calendar.DAY_OF_MONTH, days)
    }.timeInMillis

    fun startOfMonth(ts: Long): Long = Calendar.getInstance().apply {
        timeInMillis = startOfDay(ts)
        set(Calendar.DAY_OF_MONTH, 1)
    }.timeInMillis
}
