package com.rothrockware.studyjazzstandards.ui.activity

import com.rothrockware.studyjazzstandards.data.model.ActivityEntry
import com.rothrockware.studyjazzstandards.ui.components.TimelineDot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.ExperimentalTime
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

@OptIn(ExperimentalTime::class)
class ActivityFormatTest {

    private val zone = TimeZone.UTC

    private fun ts(year: Int, month: Int, day: Int, hour: Int = 12, minute: Int = 0): Long =
        LocalDateTime(year, month, day, hour, minute).toInstant(zone).toEpochMilliseconds()

    private fun entry(ts: Long, type: String = "practiced", name: String = "X") =
        ActivityEntry(ts = ts, date = 0, type = type, name = name)

    @Test
    fun dayGroupingUsesFullDateHeading() {
        assertEquals(
            "Wednesday, July 15, 2026",
            activityGroupKey(ts(2026, 7, 15), ActivityPeriod.Day, zone),
        )
    }

    @Test
    fun monthGroupingUsesMonthYear() {
        assertEquals("July 2026", activityGroupKey(ts(2026, 7, 15), ActivityPeriod.Month, zone))
    }

    @Test
    fun weekGroupingIsMondayBased() {
        // 2026-07-15 is a Wednesday; its week is Mon Jul 13 – Sun Jul 19
        assertEquals(
            "Jul 13 – Jul 19, 2026",
            activityGroupKey(ts(2026, 7, 15), ActivityPeriod.Week, zone),
        )
        // A Monday belongs to its own week
        assertEquals(
            "Jul 13 – Jul 19, 2026",
            activityGroupKey(ts(2026, 7, 13), ActivityPeriod.Week, zone),
        )
        // A Sunday closes the same week
        assertEquals(
            "Jul 13 – Jul 19, 2026",
            activityGroupKey(ts(2026, 7, 19), ActivityPeriod.Week, zone),
        )
    }

    @Test
    fun weekGroupingCrossesYearBoundary() {
        // 2026-01-01 is a Thursday; its Monday is Dec 29, 2025, Sunday Jan 4, 2026
        assertEquals(
            "Dec 29 – Jan 4, 2026",
            activityGroupKey(ts(2026, 1, 1), ActivityPeriod.Week, zone),
        )
    }

    @Test
    fun groupsPreserveNewestFirstOrder() {
        val entries = listOf(
            entry(ts(2026, 7, 6), name = "Old"),
            entry(ts(2026, 7, 15), name = "New"),
            entry(ts(2026, 7, 14), name = "Mid"),
        )
        val groups = groupActivity(entries, ActivityPeriod.Week, zone)
        assertEquals(listOf("Jul 13 – Jul 19, 2026", "Jul 6 – Jul 12, 2026"), groups.map { it.label })
        assertEquals(listOf("New", "Mid"), groups[0].entries.map { it.name })
        assertEquals(2, groups[0].entries.size)
        assertEquals(1, groups[1].entries.size)
    }

    @Test
    fun formatsTwelveHourTime() {
        assertEquals("12:05 AM", formatTime(LocalDateTime(2026, 7, 15, 0, 5)))
        assertEquals("11:59 AM", formatTime(LocalDateTime(2026, 7, 15, 11, 59)))
        assertEquals("12:00 PM", formatTime(LocalDateTime(2026, 7, 15, 12, 0)))
        assertEquals("1:07 PM", formatTime(LocalDateTime(2026, 7, 15, 13, 7)))
    }

    @Test
    fun entryMetaIncludesDateExceptForDayGrouping() {
        val t = ts(2026, 7, 15, 15, 42)
        assertEquals("3:42 PM", formatEntryMeta(t, ActivityPeriod.Day, zone))
        assertEquals("Jul 15, 2026 · 3:42 PM", formatEntryMeta(t, ActivityPeriod.Week, zone))
    }

    @Test
    fun songEditedLabelDependsOnLevelChange() {
        val changed = ActivityEntry(ts = 1, date = 0, type = "song_edited", name = "X", oldLevel = 1, level = 2)
        assertEquals("Familiarity updated to Level 2", formatActivityEntry(changed).label)

        val unchanged = ActivityEntry(ts = 1, date = 0, type = "song_edited", name = "X", oldLevel = 2, level = 2)
        assertEquals("Song details updated", formatActivityEntry(unchanged).label)

        // Legacy entries without oldLevel (web checks `!== undefined`)
        val legacy = ActivityEntry(ts = 1, date = 0, type = "song_edited", name = "X", level = 2)
        assertEquals("Song details updated", formatActivityEntry(legacy).label)
    }

    @Test
    fun reviewLabelReflectsPassFail() {
        val pass = ActivityEntry(ts = 1, date = 0, type = "review", name = "X", passed = true)
        assertEquals("Reviewed", formatActivityEntry(pass).label)
        assertEquals(TimelineDot.Green, formatActivityEntry(pass).dot)

        val fail = ActivityEntry(ts = 1, date = 0, type = "review", name = "X", passed = false)
        assertEquals("Continued", formatActivityEntry(fail).label)
        assertEquals(TimelineDot.Gold, formatActivityEntry(fail).dot)
    }

    @Test
    fun voicingPracticedLabelShowsMastery() {
        val mastered = ActivityEntry(ts = 1, date = 0, type = "voicing_practiced", name = "X", familiarity = 2)
        assertEquals("Voicing mastered", formatActivityEntry(mastered).label)
        val progress = ActivityEntry(ts = 1, date = 0, type = "voicing_practiced", name = "X", familiarity = 1)
        assertEquals("Voicing practiced — familiarity 1", formatActivityEntry(progress).label)
    }

    @Test
    fun unknownTypesFallBackToRawLabel() {
        // repertoire_removed has no case in the web formatter either
        val e = ActivityEntry(ts = 1, date = 0, type = "repertoire_removed", name = "X")
        val formatted = formatActivityEntry(e)
        assertEquals("repertoire_removed", formatted.label)
        assertEquals(TimelineDot.Dim, formatted.dot)
    }
}
