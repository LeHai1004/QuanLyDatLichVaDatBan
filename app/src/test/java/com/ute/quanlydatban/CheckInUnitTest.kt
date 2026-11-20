package com.ute.quanlydatban

import com.ute.quanlydatban.data.model.BookingStatus
import com.ute.quanlydatban.data.repository.CheckInException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckInUnitTest {

    @Test
    fun testBookingStatusTransitionsForCheckIn() {
        // Chỉ trạng thái CONFIRMED mới được chuyển sang CHECKED_IN
        assertTrue(BookingStatus.CONFIRMED.canTransitionTo(BookingStatus.CHECKED_IN))

        // Không cho phép chuyển sang CHECKED_IN nếu đơn chưa CONFIRMED
        assertFalse(BookingStatus.PENDING.canTransitionTo(BookingStatus.CHECKED_IN))
        assertFalse(BookingStatus.WAITING.canTransitionTo(BookingStatus.CHECKED_IN))

        // Không cho phép chuyển hai lần (CHECKED_IN -> CHECKED_IN)
        assertFalse(BookingStatus.CHECKED_IN.canTransitionTo(BookingStatus.CHECKED_IN))

        // Các trạng thái đã kết thúc/hủy không được check-in
        assertFalse(BookingStatus.EXPIRED.canTransitionTo(BookingStatus.CHECKED_IN))
        assertFalse(BookingStatus.NO_SHOW.canTransitionTo(BookingStatus.CHECKED_IN))
        assertFalse(BookingStatus.CANCELLED.canTransitionTo(BookingStatus.CHECKED_IN))
        assertFalse(BookingStatus.REJECTED.canTransitionTo(BookingStatus.CHECKED_IN))
        assertFalse(BookingStatus.COMPLETED.canTransitionTo(BookingStatus.CHECKED_IN))
    }

    @Test
    fun testCheckInExceptionMessagesAreVietnamese() {
        assertEquals("Mã QR sai định dạng hoặc không phải mã vị trí check-in.", CheckInException.InvalidQr.message)
        assertEquals("Không đúng quán đã đặt.", CheckInException.WrongRestaurant.message)
        assertEquals("Đơn đặt bàn chưa được xác nhận, không thể check-in.", CheckInException.NotConfirmed.message)
        assertEquals("Đơn đặt bàn đã hết hạn, không thể check-in.", CheckInException.Expired.message)
        assertEquals("Đơn đặt bàn đã bị đánh dấu vắng mặt (No-show), không thể check-in.", CheckInException.NoShow.message)
        assertEquals("Đơn đặt bàn đã bị hủy, không thể check-in.", CheckInException.Cancelled.message)
        assertEquals("Đơn đặt bàn này đã được check-in trước đó.", CheckInException.AlreadyCheckedIn.message)
    }
}
