package com.ute.quanlydatban

import com.ute.quanlydatban.data.model.Booking
import com.ute.quanlydatban.data.model.BookingStatus
import com.ute.quanlydatban.data.model.DiningTable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnerTableUnitTest {

    @Test
    fun testTableStatusComputationFromTodayBookings() {
        val table1 = DiningTable(id = "T1", name = "Bàn 01", floor = 1, capacity = 4)
        val table2 = DiningTable(id = "T2", name = "Bàn 02", floor = 1, capacity = 4)
        val table3 = DiningTable(id = "T3", name = "Bàn 03", floor = 1, capacity = 2)

        val todayBookings = listOf(
            Booking(id = "B1", tableId = "T1", status = BookingStatus.SERVING.name),
            Booking(id = "B2", tableId = "T2", status = BookingStatus.CONFIRMED.name),
            Booking(id = "B3", tableId = "T3", status = BookingStatus.CANCELLED.name)
        )

        fun computeStatus(table: DiningTable, bookings: List<Booking>): String {
            val tableBookings = bookings.filter { it.tableId == table.id }
            val serving = tableBookings.firstOrNull {
                it.status == BookingStatus.CHECKED_IN.name || it.status == BookingStatus.SERVING.name
            }
            val booked = tableBookings.firstOrNull {
                it.status == BookingStatus.CONFIRMED.name || it.status == BookingStatus.WAITING.name
            }
            return when {
                serving != null -> "serving"
                booked != null -> "booked"
                table.status == "booked" -> "booked"
                else -> "available"
            }
        }

        assertEquals("serving", computeStatus(table1, todayBookings))
        assertEquals("booked", computeStatus(table2, todayBookings))
        assertEquals("available", computeStatus(table3, todayBookings))
    }

    @Test
    fun testFloorFiltering() {
        val tables = listOf(
            DiningTable(id = "1", name = "Bàn 1", floor = 1),
            DiningTable(id = "2", name = "Bàn 2", floor = 1),
            DiningTable(id = "3", name = "Bàn 3", floor = 2),
            DiningTable(id = "4", name = "Phòng VIP 1", floor = 2, type = "room")
        )

        val floor1 = tables.filter { it.floor == 1L }
        val floor2 = tables.filter { it.floor == 2L }

        assertEquals(2, floor1.size)
        assertEquals(2, floor2.size)
        assertEquals("room", floor2[1].type)
    }

    @Test
    fun testTableValidationRules() {
        val validTable = DiningTable(
            id = "T_NEW",
            name = "Bàn 10",
            floor = 1,
            type = "table",
            capacity = 4,
            mapX = 120.0,
            mapY = 80.0
        )

        assertTrue(validTable.name.isNotBlank())
        assertTrue(validTable.capacity > 0)

        val emptyNameTable = validTable.copy(name = "   ")
        assertFalse(emptyNameTable.name.trim().isNotBlank())

        val invalidCapacityTable = validTable.copy(capacity = 0)
        assertFalse(invalidCapacityTable.capacity > 0)
    }

    @Test
    fun testTableDeletionCheckWithActiveBookings() {
        val activeBookings = listOf(
            Booking(id = "B1", tableId = "T1", status = BookingStatus.CONFIRMED.name),
            Booking(id = "B2", tableId = "T2", status = BookingStatus.COMPLETED.name),
            Booking(id = "B3", tableId = "T2", status = BookingStatus.CANCELLED.name)
        )

        fun canDeleteTable(tableId: String, bookings: List<Booking>): Boolean {
            val hasActive = bookings.any {
                it.tableId == tableId && (
                    it.status == BookingStatus.CONFIRMED.name ||
                        it.status == BookingStatus.CHECKED_IN.name ||
                        it.status == BookingStatus.SERVING.name ||
                        it.status == BookingStatus.WAITING.name
                )
            }
            return !hasActive
        }

        assertFalse(canDeleteTable("T1", activeBookings)) // Đang có CONFIRMED -> Không được xóa
        assertTrue(canDeleteTable("T2", activeBookings))  // Chỉ có COMPLETED và CANCELLED -> Được xóa
        assertTrue(canDeleteTable("T3", activeBookings))  // Không có booking nào -> Được xóa
    }
}
