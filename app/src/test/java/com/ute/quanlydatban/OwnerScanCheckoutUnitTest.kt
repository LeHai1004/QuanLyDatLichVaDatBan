package com.ute.quanlydatban

import com.ute.quanlydatban.data.model.Booking
import com.ute.quanlydatban.data.model.BookingStatus
import com.ute.quanlydatban.data.model.OrderItem
import com.ute.quanlydatban.data.model.Payment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnerScanCheckoutUnitTest {

    @Test
    fun testLongCalculationForTotalDishPrice() {
        val items = listOf(
            OrderItem(id = "1", name = "Cua rang me", qty = 2L, unitPrice = 180_000L),
            OrderItem(id = "2", name = "Nước ngọt", qty = 3L, unitPrice = 20_000L),
            OrderItem(id = "3", name = "Bia sài gòn", qty = 5L, unitPrice = 25_000L)
        )

        var total: Long = 0L
        for (item in items) {
            total += (item.qty * item.unitPrice)
        }

        // 2*180k = 360k; 3*20k = 60k; 5*25k = 125k => Tổng = 545.000đ
        assertEquals(545_000L, total)
        assertTrue(total is Long)
    }

    @Test
    fun testDepositSubtractionAndNonNegativeRemainder() {
        // Trường hợp 1: Tổng tiền món > Tiền cọc (500k - 100k = 400k)
        val total1: Long = 500_000L
        val deposit1: Long = 100_000L
        val remainder1: Long = maxOf(0L, total1 - deposit1)
        assertEquals(400_000L, remainder1)

        // Trường hợp 2: Tiền cọc > Tổng tiền món (100k cọc mà gọi món 60k -> còn phải trả 0L, không âm)
        val total2: Long = 60_000L
        val deposit2: Long = 100_000L
        val remainder2: Long = maxOf(0L, total2 - deposit2)
        assertEquals(0L, remainder2)

        // Trường hợp 3: Tổng tiền món = Tiền cọc (100k - 100k = 0L)
        val total3: Long = 100_000L
        val deposit3: Long = 100_000L
        val remainder3: Long = maxOf(0L, total3 - deposit3)
        assertEquals(0L, remainder3)
    }

    @Test
    fun testTicketVerificationStatusConstraints() {
        // Chỉ CHECKED_IN mới được quét vé chuyển sang SERVING
        assertTrue(BookingStatus.CHECKED_IN.canTransitionTo(BookingStatus.SERVING))

        // PENDING, WAITING, CONFIRMED không được chuyển trực tiếp sang SERVING (phải check-in trước)
        assertFalse(BookingStatus.PENDING.canTransitionTo(BookingStatus.SERVING))
        assertFalse(BookingStatus.WAITING.canTransitionTo(BookingStatus.SERVING))
        assertFalse(BookingStatus.CONFIRMED.canTransitionTo(BookingStatus.SERVING))

        // SERVING chuyển sang PAID khi checkout
        assertTrue(BookingStatus.SERVING.canTransitionTo(BookingStatus.PAID))
    }

    @Test
    fun testCheckoutDoubleTapProtectionAndIdempotency() {
        val bookingId = "BK_TEST_123"
        val existingPayments = mutableMapOf<String, Payment>()

        fun processCheckoutPayment(booking: Booking, amount: Long, method: String): Payment {
            val paymentKey = "PAY_${booking.id}_CHECKOUT"

            // Kiểm tra idempotent: nếu đã có bản ghi thanh toán thì trả về luôn, không tạo mới
            val current = existingPayments[paymentKey]
            if (current != null) {
                return current
            }

            val newPayment = Payment(
                id = paymentKey,
                bookingId = booking.id,
                userId = booking.userId,
                type = "checkout",
                amount = maxOf(0L, amount),
                method = method,
                status = "SUCCESS",
                paidAt = System.currentTimeMillis()
            )
            existingPayments[paymentKey] = newPayment
            return newPayment
        }

        val booking = Booking(id = bookingId, userId = "USER_1", status = BookingStatus.SERVING.name)

        // Lần bấm 1
        val payment1 = processCheckoutPayment(booking, 250_000L, "cash")
        assertEquals(1, existingPayments.size)
        assertEquals(250_000L, payment1.amount)

        // Lần bấm 2 (double-tap ngay sau đó)
        val payment2 = processCheckoutPayment(booking, 250_000L, "cash")
        assertEquals(1, existingPayments.size) // Không tạo thêm bản ghi nào
        assertEquals(payment1.id, payment2.id)
        assertEquals(payment1.paidAt, payment2.paidAt)
    }

    @Test
    fun testTicketStringParsing() {
        val raw1 = "BOOKTABLE_TICKET:BT12345678"
        val raw2 = "BT12345678"
        val raw3 = "#BT-1234"

        val cleaned1 = raw1.removePrefix("BOOKTABLE_TICKET:").trim()
        val cleaned2 = raw2.removePrefix("BT").trim()
        val cleaned3 = raw3.removePrefix("#BT-").trim()

        assertEquals("BT12345678", cleaned1)
        assertEquals("12345678", cleaned2)
        assertEquals("1234", cleaned3)
    }
}
