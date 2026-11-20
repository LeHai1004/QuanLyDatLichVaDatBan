package com.ute.quanlydatban

import com.ute.quanlydatban.data.model.DiningTable
import com.ute.quanlydatban.data.model.WaitlistEntry
import com.ute.quanlydatban.data.repository.WaitlistValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

class WaitlistUnitTest {

    private val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    @Test
    fun isPastTime_pastDateTime_returnsTrue() {
        val fixedCurrentTime = sdf.parse("24/10/2026 15:00")!!.time

        // 1 giờ trước
        val pastDate = "24/10/2026"
        val pastTime = "14:00"
        assertTrue(WaitlistValidator.isPastTime(pastDate, pastTime, fixedCurrentTime))

        // Ngày hôm trước
        val yesterdayDate = "23/10/2026"
        val yesterdayTime = "18:00"
        assertTrue(WaitlistValidator.isPastTime(yesterdayDate, yesterdayTime, fixedCurrentTime))
    }

    @Test
    fun isPastTime_futureDateTime_returnsFalse() {
        val fixedCurrentTime = sdf.parse("24/10/2026 15:00")!!.time

        // 1 giờ sau
        val futureDate = "24/10/2026"
        val futureTime = "16:00"
        assertFalse(WaitlistValidator.isPastTime(futureDate, futureTime, fixedCurrentTime))

        // Ngày mai
        val tomorrowDate = "25/10/2026"
        val tomorrowTime = "12:00"
        assertFalse(WaitlistValidator.isPastTime(tomorrowDate, tomorrowTime, fixedCurrentTime))
    }

    @Test
    fun hasDuplicateWaiting_sameUserSameSlotWaiting_returnsTrue() {
        val existingEntries = listOf(
            WaitlistEntry(
                id = "W1",
                userId = "USER_A",
                restaurantId = "REST_1",
                date = "25/10/2026",
                time = "18:30",
                status = "waiting"
            ),
            WaitlistEntry(
                id = "W2",
                userId = "USER_B",
                restaurantId = "REST_1",
                date = "25/10/2026",
                time = "18:30",
                status = "waiting"
            )
        )

        // Cùng USER_A, REST_1, ngày và giờ -> trùng
        val isDuplicate = WaitlistValidator.hasDuplicateWaiting(
            existingEntries = existingEntries,
            userId = "USER_A",
            restaurantId = "REST_1",
            date = "25/10/2026",
            time = "18:30"
        )
        assertTrue(isDuplicate)

        // Khác giờ -> không trùng
        val differentTime = WaitlistValidator.hasDuplicateWaiting(
            existingEntries = existingEntries,
            userId = "USER_A",
            restaurantId = "REST_1",
            date = "25/10/2026",
            time = "19:00"
        )
        assertFalse(differentTime)
    }

    @Test
    fun hasDuplicateWaiting_previousEntryExpiredOrNotified_allowsNewRegistration() {
        val existingEntries = listOf(
            WaitlistEntry(
                id = "W1",
                userId = "USER_A",
                restaurantId = "REST_1",
                date = "25/10/2026",
                time = "18:30",
                status = "expired"
            ),
            WaitlistEntry(
                id = "W2",
                userId = "USER_A",
                restaurantId = "REST_1",
                date = "24/10/2026",
                time = "18:30",
                status = "notified"
            )
        )

        val isDuplicate = WaitlistValidator.hasDuplicateWaiting(
            existingEntries = existingEntries,
            userId = "USER_A",
            restaurantId = "REST_1",
            date = "25/10/2026",
            time = "18:30"
        )
        assertFalse(isDuplicate)
    }

    @Test
    fun findSuitableTable_availableTableFound_returnsTable() {
        val tables = listOf(
            DiningTable(id = "T1", capacity = 2),
            DiningTable(id = "T2", capacity = 4),
            DiningTable(id = "T3", capacity = 6)
        )

        // T1 đã bị đặt, khách đặt cho 4 người -> T2 còn trống và đủ chỗ
        val bookedIds = setOf("T1")
        val suitableTable = WaitlistValidator.findSuitableTable(tables, bookedIds, 4)

        assertNotNull(suitableTable)
        assertEquals("T2", suitableTable?.id)
    }

    @Test
    fun findSuitableTable_allSuitableTablesBooked_returnsNull() {
        val tables = listOf(
            DiningTable(id = "T1", capacity = 2),
            DiningTable(id = "T2", capacity = 4)
        )

        // Cả T1 và T2 đều đã bị đặt
        val bookedIds = setOf("T1", "T2")
        val suitableTable = WaitlistValidator.findSuitableTable(tables, bookedIds, 2)

        assertNull(suitableTable)
    }

    @Test
    fun findSuitableTable_guestsExceedAnyTableCapacity_returnsNull() {
        val tables = listOf(
            DiningTable(id = "T1", capacity = 2),
            DiningTable(id = "T2", capacity = 4)
        )

        // Khách đi 10 người, không bàn nào đủ sức chứa
        val bookedIds = emptySet<String>()
        val suitableTable = WaitlistValidator.findSuitableTable(tables, bookedIds, 10)

        assertNull(suitableTable)
    }

    @Test
    fun findNextCandidate_fifoAndCapacityMatching() {
        val fixedCurrentTime = sdf.parse("25/10/2026 15:00")!!.time

        val entries = listOf(
            WaitlistEntry(
                id = "W1",
                guests = 4,
                createdAt = 2000L,
                date = "25/10/2026",
                time = "18:30",
                status = "waiting"
            ),
            WaitlistEntry(
                id = "W2",
                guests = 2,
                createdAt = 1000L,
                date = "25/10/2026",
                time = "18:30",
                status = "waiting"
            ),
            WaitlistEntry(
                id = "W3",
                guests = 6,
                createdAt = 500L,
                date = "25/10/2026",
                time = "18:30",
                status = "waiting"
            )
        )

        // 1. Khi bàn trống có sức chứa 4:
        // W3 (6 người) không vừa. W1 (4 người, t=2000) và W2 (2 người, t=1000) đều vừa.
        // FIFO -> W2 đăng ký sớm hơn (t=1000 < 2000) nên được chọn.
        val nextCandidate4 = WaitlistValidator.findNextCandidate(entries, freedTableCapacity = 4, currentTimeMillis = fixedCurrentTime)
        assertNotNull(nextCandidate4)
        assertEquals("W2", nextCandidate4?.id)

        // 2. Khi bàn trống có sức chứa 8:
        // Cả 3 người đều vừa, W3 đăng ký sớm nhất (t=500) được chọn.
        val nextCandidate8 = WaitlistValidator.findNextCandidate(entries, freedTableCapacity = 8, currentTimeMillis = fixedCurrentTime)
        assertNotNull(nextCandidate8)
        assertEquals("W3", nextCandidate8?.id)

        // 3. Khi bàn trống có sức chứa 1: không ai vừa
        val nextCandidate1 = WaitlistValidator.findNextCandidate(entries, freedTableCapacity = 1, currentTimeMillis = fixedCurrentTime)
        assertNull(nextCandidate1)
    }

    @Test
    fun findNextCandidate_skipsExpiredEntries() {
        val fixedCurrentTime = sdf.parse("25/10/2026 19:00")!!.time

        val entries = listOf(
            WaitlistEntry(
                id = "W_OLD",
                guests = 2,
                createdAt = 100L,
                date = "25/10/2026",
                time = "18:00", // Đã qua so với 19:00
                status = "waiting"
            ),
            WaitlistEntry(
                id = "W_VALID",
                guests = 2,
                createdAt = 200L,
                date = "25/10/2026",
                time = "20:00", // Vẫn trong tương lai
                status = "waiting"
            )
        )

        val nextCandidate = WaitlistValidator.findNextCandidate(entries, freedTableCapacity = 4, currentTimeMillis = fixedCurrentTime)
        assertNotNull(nextCandidate)
        assertEquals("W_VALID", nextCandidate?.id)
    }
}
