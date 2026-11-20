package com.example.data.model

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

object CurrencyFormatter {
    fun formatMinor(
        amountMinor: Long,
        currency: CurrencyOption,
        includeSign: Boolean = false,
        forceDecimals: Boolean = false
    ): String {
        val isNegative = amountMinor < 0
        val absMinor = abs(amountMinor)
        val whole = absMinor / 100
        val fraction = (absMinor % 100).toInt()

        val symbols = DecimalFormatSymbols(Locale.US)
        val pattern = if (currency.code == "INR") "#,##,##0" else "#,##0"
        val formattedWhole = if (currency.code == "INR") {
            formatIndianGrouping(whole)
        } else {
            DecimalFormat(pattern, symbols).format(whole)
        }

        val amountText = if (fraction > 0 || forceDecimals) {
            "$formattedWhole.${fraction.toString().padStart(2, '0')}"
        } else {
            formattedWhole
        }

        val prefix = when {
            isNegative -> "-${currency.symbol}"
            includeSign && amountMinor > 0 -> "+${currency.symbol}"
            else -> currency.symbol
        }
        return "$prefix$amountText"
    }

    private fun formatIndianGrouping(number: Long): String {
        val str = number.toString()
        if (str.length <= 3) return str
        val lastThree = str.takeLast(3)
        val rest = str.dropLast(3)
        val sb = StringBuilder()
        var count = 0
        for (i in rest.length - 1 downTo 0) {
            sb.append(rest[i])
            count++
            if (count % 2 == 0 && i != 0) {
                sb.append(',')
            }
        }
        return "${sb.reverse()},$lastThree"
    }

    fun parseMajorToMinor(input: String): Long? {
        val cleaned = input.trim().replace(",", "")
        if (cleaned.isEmpty()) return null
        val value = cleaned.toDoubleOrNull() ?: return null
        if (value < 0.0 || value.isNaN() || value.isInfinite()) return null
        return (value * 100.0).roundToLong()
    }

    fun minorToEditableString(amountMinor: Long): String {
        val whole = amountMinor / 100
        val fraction = abs(amountMinor % 100).toInt()
        return if (fraction == 0) {
            whole.toString()
        } else {
            "$whole.${fraction.toString().padStart(2, '0')}"
        }
    }
}

object DateFormatters {
    private val dayMonthYear = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    private val shortDayMonth = SimpleDateFormat("dd MMM", Locale.getDefault())
    private val monthYear = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
    private val shortMonth = SimpleDateFormat("MMM", Locale.getDefault())
    private val isoDate = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun formatDate(millis: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        val today = Calendar.getInstance()
        val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        return when {
            isSameDay(cal, today) -> "Today"
            isSameDay(cal, yesterday) -> "Yesterday"
            else -> dayMonthYear.format(Date(millis))
        }
    }

    fun formatFullDate(millis: Long): String = dayMonthYear.format(Date(millis))

    fun formatShortDate(millis: Long): String = shortDayMonth.format(Date(millis))

    fun formatIsoDate(millis: Long): String = isoDate.format(Date(millis))

    fun formatMonthYear(year: Int, monthZeroBased: Int): String {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, monthZeroBased)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        return monthYear.format(cal.time)
    }

    fun formatShortMonth(year: Int, monthZeroBased: Int): String {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, monthZeroBased)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        return shortMonth.format(cal.time)
    }

    fun startOfTodayMillis(): Long {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    fun startOfYesterdayMillis(): Long {
        return Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -1)
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    fun monthBoundsMillis(year: Int, monthZeroBased: Int): Pair<Long, Long> {
        val start = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, monthZeroBased)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val end = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, monthZeroBased)
            set(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis - 1L

        return start to end
    }

    private fun isSameDay(a: Calendar, b: Calendar): Boolean =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
            a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
}
