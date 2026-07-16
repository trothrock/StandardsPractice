package com.rothrockware.studyjazzstandards.ui.activity

import com.rothrockware.studyjazzstandards.data.DateInt
import com.rothrockware.studyjazzstandards.data.DateInt.toDateInt
import com.rothrockware.studyjazzstandards.data.model.ActivityEntry
import com.rothrockware.studyjazzstandards.data.model.ActivityType
import com.rothrockware.studyjazzstandards.ui.components.TimelineDot
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

enum class ActivityPeriod(val label: String) { Day("Day"), Week("Week"), Month("Month") }

data class FormattedEntry(val dot: TimelineDot, val label: String, val detail: String)

data class ActivityGroup(val label: String, val entries: List<ActivityEntry>)

private val MONTHS_FULL = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)

private val WEEKDAYS = listOf(
    "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday",
)

/** "Wednesday, July 15, 2026" — matches the web app's en-US day heading. */
fun formatFullDate(date: LocalDate): String =
    "${WEEKDAYS[date.dayOfWeek.isoDayNumber - 1]}, ${MONTHS_FULL[date.month.number - 1]} ${date.day}, ${date.year}"

fun formatFullDate(dateInt: Int): String = formatFullDate(DateInt.run { dateInt.toLocalDate() })

/** "3:42 PM" */
fun formatTime(time: LocalDateTime): String {
    val hour24 = time.hour
    val hour = when {
        hour24 == 0 -> 12
        hour24 > 12 -> hour24 - 12
        else -> hour24
    }
    val ampm = if (hour24 < 12) "AM" else "PM"
    val minute = time.minute.toString().padStart(2, '0')
    return "$hour:$minute $ampm"
}

@OptIn(ExperimentalTime::class)
fun tsToLocalDateTime(ts: Long, zone: TimeZone): LocalDateTime =
    Instant.fromEpochMilliseconds(ts).toLocalDateTime(zone)

/** Port of the web app's formatActivityEntry — including the default-branch fallback. */
fun formatActivityEntry(entry: ActivityEntry): FormattedEntry = when (entry.type) {
    ActivityType.STARTED_LEARNING -> FormattedEntry(TimelineDot.Blue, "Started learning", entry.name.orEmpty())
    ActivityType.PRACTICED -> FormattedEntry(TimelineDot.Gold, "Practiced", entry.name.orEmpty())
    ActivityType.LEARNED -> FormattedEntry(TimelineDot.Green, "Marked as Level 2", entry.name.orEmpty())
    ActivityType.REVIEW -> FormattedEntry(
        if (entry.passed == true) TimelineDot.Green else TimelineDot.Gold,
        if (entry.passed == true) "Reviewed" else "Continued",
        entry.name.orEmpty(),
    )
    ActivityType.SONG_ADDED -> FormattedEntry(TimelineDot.Blue, "Added to library", entry.name.orEmpty())
    ActivityType.SONG_EDITED -> FormattedEntry(
        TimelineDot.Dim,
        if (entry.oldLevel != null && entry.oldLevel != entry.level) {
            "Familiarity updated to Level ${entry.level}"
        } else {
            "Song details updated"
        },
        entry.name ?: entry.oldName.orEmpty(),
    )
    ActivityType.STOPPED_LEARNING -> FormattedEntry(TimelineDot.Dim, "Stopped learning", entry.name.orEmpty())
    ActivityType.SONG_REMOVED -> FormattedEntry(TimelineDot.Red, "Removed from library", entry.name.orEmpty())
    ActivityType.REPERTOIRE_ADDED -> FormattedEntry(TimelineDot.Blue, "Repertoire added", entry.name.orEmpty())
    ActivityType.REPERTOIRE_REVIEWED -> FormattedEntry(TimelineDot.Green, "Repertoire reviewed", entry.name.orEmpty())
    ActivityType.REPERTOIRE_DEFERRED -> FormattedEntry(TimelineDot.Gold, "Repertoire continued", entry.name.orEmpty())
    ActivityType.VOICING_ADDED -> FormattedEntry(TimelineDot.Blue, "Voicing added", entry.name.orEmpty())
    ActivityType.VOICING_PRACTICED -> FormattedEntry(
        TimelineDot.Green,
        if ((entry.familiarity ?: 0) >= 2) "Voicing mastered" else "Voicing practiced — familiarity ${entry.familiarity}",
        entry.name.orEmpty(),
    )
    ActivityType.VOICING_REMOVED -> FormattedEntry(TimelineDot.Red, "Voicing removed", entry.name.orEmpty())
    // Web parity: unknown types (e.g. repertoire_removed) fall through with a dim dot.
    else -> FormattedEntry(TimelineDot.Dim, entry.type, entry.name.orEmpty())
}

/** Grouping key per the web app: day heading, Monday-based week range, or month. */
fun activityGroupKey(ts: Long, period: ActivityPeriod, zone: TimeZone): String {
    val dt = tsToLocalDateTime(ts, zone)
    val date = dt.date
    return when (period) {
        ActivityPeriod.Day -> formatFullDate(date)
        ActivityPeriod.Month -> "${MONTHS_FULL[date.month.number - 1]} ${date.year}"
        ActivityPeriod.Week -> {
            val monday = date.minus(date.dayOfWeek.isoDayNumber - 1, DateTimeUnit.DAY)
            val sunday = monday.plus(6, DateTimeUnit.DAY)
            "${DateInt.formatShort(monday.toDateInt())} – ${DateInt.formatShort(sunday.toDateInt())}, ${sunday.year}"
        }
    }
}

/** Newest-first entries grouped preserving first-seen group order (web parity). */
fun groupActivity(
    entries: List<ActivityEntry>,
    period: ActivityPeriod,
    zone: TimeZone,
): List<ActivityGroup> {
    val sorted = entries.sortedByDescending { it.ts }
    val order = mutableListOf<String>()
    val byKey = mutableMapOf<String, MutableList<ActivityEntry>>()
    sorted.forEach { entry ->
        val key = activityGroupKey(entry.ts, period, zone)
        byKey.getOrPut(key) {
            order.add(key)
            mutableListOf()
        }.add(entry)
    }
    return order.map { ActivityGroup(it, byKey.getValue(it)) }
}

/** "Jul 15, 2026 · 3:42 PM" (or time only for day grouping). */
fun formatEntryMeta(ts: Long, period: ActivityPeriod, zone: TimeZone): String {
    val dt = tsToLocalDateTime(ts, zone)
    val time = formatTime(dt)
    return if (period == ActivityPeriod.Day) time
    else "${DateInt.formatIntDate(dt.date.toDateInt())} · $time"
}
