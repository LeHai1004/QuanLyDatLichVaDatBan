package com.ute.quanlydatban

import com.ute.quanlydatban.work.ReminderScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

class BookingReminderUnitTest {

    private val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    @Test
    fun testDelayMoreThanOneHour() {
        val baseDateStr = "20/11/2026"
        val baseTimeStr = "14:00"
        val bookingTimeMillis = sdf.parse("$baseDateStr $baseTimeStr")!!.time

        // Exactly 2 hours before booking
        val nowMillis = bookingTimeMillis - 2 * 60 * 60 * 1000L
        val delay = ReminderScheduler.calculateDelay(baseDateStr, baseTimeStr, nowMillis)

        assertNotNull(delay)
        // Delay should be exactly 1 hour (bookingTime - 1h - now)
        assertEquals(60 * 60 * 1000L, delay)
    }

    @Test
    fun testDelayLessThanOneHour_ReturnsZero() {
        val baseDateStr = "20/11/2026"
        val baseTimeStr = "14:00"
        val bookingTimeMillis = sdf.parse("$baseDateStr $baseTimeStr")!!.time

        // 45 minutes before booking (< 1h)
        val nowMillis = bookingTimeMillis - 45 * 60 * 1000L
        val delay = ReminderScheduler.calculateDelay(baseDateStr, baseTimeStr, nowMillis)

        assertNotNull(delay)
        assertEquals(0L, delay)
    }

    @Test
    fun testPastBooking_ReturnsNull() {
        val baseDateStr = "20/11/2026"
        val baseTimeStr = "14:00"
        val bookingTimeMillis = sdf.parse("$baseDateStr $baseTimeStr")!!.time

        // 10 minutes after booking (in the past)
        val nowMillis = bookingTimeMillis + 10 * 60 * 1000L
        val delay = ReminderScheduler.calculateDelay(baseDateStr, baseTimeStr, nowMillis)

        assertNull(delay)
    }

    @Test
    fun testInvalidDateTime_ReturnsNull() {
        val nowMillis = 1700000000000L

        assertNull(ReminderScheduler.calculateDelay("", "18:00", nowMillis))
        assertNull(ReminderScheduler.calculateDelay("20/11/2026", "", nowMillis))
        assertNull(ReminderScheduler.calculateDelay("invalid-date", "invalid-time", nowMillis))
    }

    @Test
    fun testWorkNamePrefix() {
        val bookingId = "BOOKING_9999"
        val expected = "booking_reminder_BOOKING_9999"
        assertEquals(expected, ReminderScheduler.getWorkName(bookingId))
    }
}
