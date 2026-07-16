package com.rothrockware.studyjazzstandards.data

import kotlin.test.Test
import kotlin.test.assertEquals

class DateIntTest {

    @Test
    fun addDaysCrossesMonthBoundary() {
        assertEquals(20260301, DateInt.addDays(20260228, 1))
        assertEquals(20260731, DateInt.addDays(20260715, 16))
    }

    @Test
    fun addDaysHandlesLeapYear() {
        assertEquals(20240229, DateInt.addDays(20240228, 1))
        assertEquals(20240301, DateInt.addDays(20240229, 1))
    }

    @Test
    fun addDaysCrossesYearBoundary() {
        assertEquals(20260101, DateInt.addDays(20251231, 1))
        assertEquals(20251231, DateInt.addDays(20260101, -1))
    }

    @Test
    fun daysBetweenIsSignedDayDifference() {
        assertEquals(31, DateInt.daysBetween(20260101, 20260201))
        assertEquals(-31, DateInt.daysBetween(20260201, 20260101))
        assertEquals(0, DateInt.daysBetween(20260715, 20260715))
        assertEquals(366, DateInt.daysBetween(20240101, 20250101))
    }

    @Test
    fun formatsEnglishDates() {
        assertEquals("Jul 15, 2026", DateInt.formatIntDate(20260715))
        assertEquals("Jan 1, 2020", DateInt.formatIntDate(20200101))
        assertEquals("July 2026", DateInt.formatMonthYear(20260715))
        assertEquals("Jul 15", DateInt.formatShort(20260715))
    }
}
