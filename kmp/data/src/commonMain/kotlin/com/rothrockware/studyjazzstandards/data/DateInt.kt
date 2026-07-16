package com.rothrockware.studyjazzstandards.data

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.number
import kotlinx.datetime.plus

/**
 * Dates are stored as integer yyyymmdd (e.g. 20260715), matching the web app.
 */
object DateInt {

    fun Int.toLocalDate(): LocalDate =
        LocalDate(this / 10000, (this % 10000) / 100, this % 100)

    fun LocalDate.toDateInt(): Int =
        year * 10000 + month.number * 100 + day

    fun addDays(dateInt: Int, n: Int): Int =
        dateInt.toLocalDate().plus(n, DateTimeUnit.DAY).toDateInt()

    fun daysBetween(from: Int, to: Int): Int =
        from.toLocalDate().daysUntil(to.toLocalDate())

    private val MONTHS = listOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
    )

    private val MONTHS_FULL = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December",
    )

    /** "Mar 5, 2026" — locale-stable English, like the web app's formatting. */
    fun formatIntDate(dateInt: Int): String {
        val d = dateInt.toLocalDate()
        return "${MONTHS[d.month.number - 1]} ${d.day}, ${d.year}"
    }

    /** "March 2026" */
    fun formatMonthYear(dateInt: Int): String {
        val d = dateInt.toLocalDate()
        return "${MONTHS_FULL[d.month.number - 1]} ${d.year}"
    }

    /** "Mar 5" */
    fun formatShort(dateInt: Int): String {
        val d = dateInt.toLocalDate()
        return "${MONTHS[d.month.number - 1]} ${d.day}"
    }
}
