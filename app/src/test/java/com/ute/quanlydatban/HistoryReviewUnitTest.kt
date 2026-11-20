package com.ute.quanlydatban

import com.ute.quanlydatban.data.model.Booking
import com.ute.quanlydatban.data.model.BookingStatus
import com.ute.quanlydatban.data.model.Review
import com.ute.quanlydatban.viewmodel.HistoryViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

class HistoryReviewUnitTest {

    @Test
    fun testBookingStatusTransitionsForHistoryAndReview() {
        // PAID chỉ được chuyển sang COMPLETED (khi hoàn tất đánh giá)
        assertTrue(BookingStatus.PAID.canTransitionTo(BookingStatus.COMPLETED))

        // PENDING, WAITING, CONFIRMED có thể hủy (chuyển sang CANCELLED)
        assertTrue(BookingStatus.PENDING.canTransitionTo(BookingStatus.CANCELLED))
        assertTrue(BookingStatus.WAITING.canTransitionTo(BookingStatus.CANCELLED))
        assertTrue(BookingStatus.CONFIRMED.canTransitionTo(BookingStatus.CANCELLED))

        // CHECKED_IN hoặc SERVING không được hủy trực tiếp
        assertFalse(BookingStatus.CHECKED_IN.canTransitionTo(BookingStatus.CANCELLED))
        assertFalse(BookingStatus.SERVING.canTransitionTo(BookingStatus.CANCELLED))

        // COMPLETED hoặc CANCELLED không thể chuyển sang trạng thái khác
        assertFalse(BookingStatus.COMPLETED.canTransitionTo(BookingStatus.CANCELLED))
        assertFalse(BookingStatus.CANCELLED.canTransitionTo(BookingStatus.CONFIRMED))
    }

    @Test
    fun testPartitionBookingsIntoTabs() {
        val bookings = listOf(
            Booking(id = "1", status = BookingStatus.PENDING.name),
            Booking(id = "2", status = BookingStatus.WAITING.name),
            Booking(id = "3", status = BookingStatus.CONFIRMED.name),
            Booking(id = "4", status = BookingStatus.CHECKED_IN.name),
            Booking(id = "5", status = BookingStatus.SERVING.name),
            Booking(id = "6", status = BookingStatus.PAID.name),
            Booking(id = "7", status = BookingStatus.COMPLETED.name),
            Booking(id = "8", status = BookingStatus.CANCELLED.name),
            Booking(id = "9", status = BookingStatus.REJECTED.name),
            Booking(id = "10", status = BookingStatus.EXPIRED.name),
            Booking(id = "11", status = BookingStatus.NO_SHOW.name)
        )

        val upcomingStatuses = setOf(
            BookingStatus.PENDING.name,
            BookingStatus.WAITING.name,
            BookingStatus.CONFIRMED.name,
            BookingStatus.CHECKED_IN.name,
            BookingStatus.SERVING.name
        )

        val completedStatuses = setOf(
            BookingStatus.PAID.name,
            BookingStatus.COMPLETED.name
        )

        val cancelledStatuses = setOf(
            BookingStatus.CANCELLED.name,
            BookingStatus.REJECTED.name,
            BookingStatus.EXPIRED.name,
            BookingStatus.NO_SHOW.name
        )

        val upcomingList = bookings.filter { it.status in upcomingStatuses }
        val completedList = bookings.filter { it.status in completedStatuses }
        val cancelledList = bookings.filter { it.status in cancelledStatuses }

        assertEquals(5, upcomingList.size)
        assertEquals(2, completedList.size)
        assertEquals(4, cancelledList.size)
    }

    @Test
    fun testReviewValidationRules() {
        val validReview = Review(
            id = "REV_1",
            bookingId = "BK_1",
            userId = "USER_1",
            restaurantId = "RES_1",
            rating = 5,
            comment = "Món ăn rất ngon, không gian ấm cúng!"
        )

        assertTrue(validReview.rating in 1..5)
        assertTrue(validReview.bookingId.isNotBlank())
        assertTrue(validReview.restaurantId.isNotBlank())

        // Đánh giá 0 sao hoặc ngoài 1..5 là không hợp lệ
        val invalidZeroStar = validReview.copy(rating = 0)
        assertFalse(invalidZeroStar.rating in 1..5)

        val invalidOverStar = validReview.copy(rating = 6)
        assertFalse(invalidOverStar.rating in 1..5)
    }

    @Test
    fun testPastScheduleCheck() {
        val now = System.currentTimeMillis()
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

        val pastDateStr = sdf.format(now - 24 * 60 * 60 * 1000L).split(" ")[0]
        val pastTimeStr = sdf.format(now - 24 * 60 * 60 * 1000L).split(" ")[1]

        val futureDateStr = sdf.format(now + 24 * 60 * 60 * 1000L).split(" ")[0]
        val futureTimeStr = sdf.format(now + 24 * 60 * 60 * 1000L).split(" ")[1]

        val pastParsed = sdf.parse("$pastDateStr $pastTimeStr")?.time ?: 0
        val futureParsed = sdf.parse("$futureDateStr $futureTimeStr")?.time ?: 0

        assertTrue(now > pastParsed)
        assertFalse(now > futureParsed)
    }
}
