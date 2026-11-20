package com.ute.quanlydatban.data.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.ute.quanlydatban.data.model.Booking
import com.ute.quanlydatban.data.model.BookingStatus
import com.ute.quanlydatban.data.model.Payment

class PaymentRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val bookingRepository: BookingRepository = BookingRepository()
) : BaseRepository<Payment>(firestore.collection("payments"), Payment::class.java) {

    companion object {
        private const val TAG = "PaymentRepository"
        const val PAYMENT_TYPE_DEPOSIT = "deposit"
        const val PAYMENT_METHOD_VIETQR = "vietqr"
        const val PAYMENT_STATUS_COMPLETED = "completed"
        const val PAYMENT_STATUS_PENDING = "pending"
    }

    /**
     * Xác nhận thanh toán cọc và chuyển trạng thái đơn đặt bàn từ PENDING sang WAITING
     * Sử dụng transaction để chống bấm nhiều lần (idempotent), tránh ghi trùng payment
     */
    fun confirmDepositPayment(
        bookingId: String,
        userId: String,
        amount: Long,
        onResult: (Result<Payment>) -> Unit
    ) {
        if (bookingId.isBlank()) {
            val err = IllegalArgumentException("bookingId không được để trống")
            Log.e(TAG, err.message ?: "")
            onResult(Result.failure(err))
            return
        }

        val bookingRef = firestore.collection("bookings").document(bookingId)
        val paymentId = "PAY_${bookingId}_DEPOSIT"
        val paymentRef = collection.document(paymentId)
        var capturedBooking: Booking? = null

        firestore.runTransaction { transaction ->
            val bookingSnap = transaction.get(bookingRef)
            if (!bookingSnap.exists()) {
                throw IllegalStateException("Không tìm thấy đơn đặt bàn #$bookingId")
            }

            val currentStatus = bookingSnap.getString("status") ?: BookingStatus.PENDING.name
            val bookingStatus = BookingStatus.from(currentStatus)

            // Kiểm tra trạng thái hợp lệ
            if (bookingStatus == BookingStatus.EXPIRED) {
                throw IllegalStateException("BOOKING_EXPIRED")
            }
            if (bookingStatus == BookingStatus.WAITING || bookingStatus == BookingStatus.CONFIRMED) {
                // Đã được xác nhận thanh toán trước đó
                val existingPaymentSnap = transaction.get(paymentRef)
                if (existingPaymentSnap.exists()) {
                    return@runTransaction existingPaymentSnap.toObject(Payment::class.java)!!
                }
            }

            if (!bookingStatus.canTransitionTo(BookingStatus.WAITING)) {
                throw IllegalStateException("Không thể chuyển trạng thái từ $bookingStatus sang WAITING")
            }

            val now = System.currentTimeMillis()
            val qrCodeValue = bookingSnap.getString("qrCode")?.takeIf { it.isNotBlank() } ?: "BT_${bookingId}"

            val bookingObj = bookingSnap.toObject(Booking::class.java) ?: Booking(id = bookingId)
            capturedBooking = bookingObj.copy(
                id = bookingId,
                userId = userId.ifEmpty { bookingObj.userId },
                restaurantId = bookingSnap.getString("restaurantId").orEmpty().ifEmpty { bookingObj.restaurantId },
                depositAmount = amount,
                status = BookingStatus.WAITING.name
            )

            val payment = Payment(
                id = paymentId,
                bookingId = bookingId,
                userId = userId,
                type = PAYMENT_TYPE_DEPOSIT,
                amount = amount,
                method = PAYMENT_METHOD_VIETQR,
                status = PAYMENT_STATUS_COMPLETED,
                paidAt = now
            )

            // Cập nhật trạng thái booking sang WAITING và lưu qrCode
            transaction.update(
                bookingRef,
                mapOf(
                    "status" to BookingStatus.WAITING.name,
                    "depositAmount" to amount,
                    "qrCode" to qrCodeValue
                )
            )

            // Lưu bản ghi payment (idempotent qua fixed document ID)
            transaction.set(paymentRef, payment)
            payment
        }.addOnSuccessListener { payment ->
            val bookingToNotify = capturedBooking ?: Booking(id = bookingId, userId = userId, status = BookingStatus.WAITING.name)
            Log.e("NotiEvent", "confirmDepositPayment success -> bookingId: $bookingId, status: WAITING")
            NotificationRepository().createEventNotification(bookingToNotify, BookingStatus.WAITING)
            onResult(Result.success(payment))
        }.addOnFailureListener { error ->
            Log.e(TAG, "Lỗi confirmDepositPayment: ${error.message}", error)
            onResult(Result.failure(error))
        }
    }

    /**
     * Tải chi tiết đơn đặt bàn
     */
    fun getBooking(bookingId: String, onResult: (Result<Booking?>) -> Unit) {
        if (bookingId.isBlank()) {
            onResult(Result.success(null))
            return
        }
        bookingRepository.get(bookingId, onResult)
    }

    /**
     * Đánh dấu booking hết hạn (EXPIRED) khi quá 10 phút chưa cọc
     */
    fun markBookingExpired(bookingId: String, onResult: (Result<Unit>) -> Unit) {
        bookingRepository.updateBookingStatus(bookingId, BookingStatus.EXPIRED, onResult)
    }
}
