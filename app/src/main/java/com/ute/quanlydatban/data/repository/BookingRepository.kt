package com.ute.quanlydatban.data.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.ute.quanlydatban.data.model.Booking
import com.ute.quanlydatban.data.model.BookingStatus
import com.ute.quanlydatban.data.model.DiningTable
import com.ute.quanlydatban.data.model.Restaurant

class BookingRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) : BaseRepository<Booking>(firestore.collection("bookings"), Booking::class.java) {

    companion object {
        const val ROLE_CUSTOMER = "customer"
        const val ROLE_OWNER = "owner"
        const val BOOKING_HOLD_TIMEOUT_MS = 10 * 60 * 1000L // 10 phút giữ bàn
        const val DEFAULT_DEPOSIT_AMOUNT = 100_000L
        private const val TAG = "BookingRepository"
    }

    // Lấy thông tin nhà hàng cho màn đặt bàn
    fun getRestaurant(restaurantId: String, onResult: (Result<Restaurant?>) -> Unit) {
        if (restaurantId.isBlank()) {
            Log.e(TAG, "getRestaurant: restaurantId rỗng")
            onResult(Result.success(null))
            return
        }
        firestore.collection("restaurants").document(restaurantId).get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    onResult(Result.success(snapshot.toObject(Restaurant::class.java)))
                } else {
                    Log.e(TAG, "Không tìm thấy quán với id=$restaurantId")
                    onResult(Result.success(null))
                }
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "Lỗi getRestaurant($restaurantId): ${error.message}", error)
                onResult(Result.failure(error))
            }
    }

    // Lấy danh sách bàn theo quán và tự động giải phóng các bàn giữ chỗ quá 10 phút
    fun getTables(restaurantId: String, onResult: (Result<List<DiningTable>>) -> Unit) {
        if (restaurantId.isBlank()) {
            Log.e(TAG, "getTables: restaurantId rỗng")
            onResult(Result.success(emptyList()))
            return
        }

        // Tự động kiểm tra các đơn PENDING quá 10 phút chưa cọc để hoàn trả bàn
        cleanupExpiredBookings(restaurantId)

        firestore.collection("restaurants").document(restaurantId)
            .collection("tables")
            .get()
            .addOnSuccessListener { snapshot ->
                val tables = snapshot.toObjects(DiningTable::class.java)
                onResult(Result.success(tables))
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "Lỗi getTables($restaurantId): ${error.message}", error)
                onResult(Result.failure(error))
            }
    }

    // Giải phóng bàn cho các đơn quá 10 phút chưa hoàn tất cọc
    private fun cleanupExpiredBookings(restaurantId: String) {
        val now = System.currentTimeMillis()
        val expiredThreshold = now - BOOKING_HOLD_TIMEOUT_MS

        collection
            .whereEqualTo("restaurantId", restaurantId)
            .whereEqualTo("status", BookingStatus.PENDING.name)
            .get()
            .addOnSuccessListener { snapshot ->
                for (doc in snapshot.documents) {
                    val booking = doc.toObject(Booking::class.java) ?: continue
                    if (booking.createdAt in 1..expiredThreshold) {
                        // Đơn đã quá 10 phút: đổi trạng thái sang EXPIRED và trả bàn
                        updateBookingStatus(booking.id, BookingStatus.EXPIRED) { result ->
                            result.onSuccess {
                                Log.i(TAG, "Đã hủy đơn hết hạn #${booking.id} và trả bàn ${booking.tableId}")
                            }
                        }
                    }
                }
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "Lỗi cleanupExpiredBookings: ${error.message}", error)
            }
    }

    // Đổi trạng thái đơn đặt bàn qua máy trạng thái BookingStatus
    fun updateBookingStatus(
        bookingId: String,
        nextStatus: BookingStatus,
        onResult: (Result<Unit>) -> Unit
    ) {
        if (bookingId.isBlank()) {
            val err = IllegalArgumentException("bookingId không được để trống")
            Log.e(TAG, err.message ?: "")
            onResult(Result.failure(err))
            return
        }

        collection.document(bookingId).get()
            .addOnSuccessListener { snapshot ->
                val booking = snapshot.toObject(Booking::class.java)
                if (booking == null) {
                    val err = IllegalStateException("Không tìm thấy đơn đặt bàn #$bookingId")
                    Log.e(TAG, err.message ?: "")
                    onResult(Result.failure(err))
                    return@addOnSuccessListener
                }

                val currentStatus = BookingStatus.from(booking.status)
                if (!currentStatus.canTransitionTo(nextStatus)) {
                    val err = IllegalStateException("Không thể chuyển trạng thái từ $currentStatus sang $nextStatus")
                    Log.e(TAG, err.message ?: "")
                    onResult(Result.failure(err))
                    return@addOnSuccessListener
                }

                collection.document(bookingId).update("status", nextStatus.name)
                    .addOnSuccessListener {
                        // Khi đơn hết hạn hoặc bị hủy, giải phóng bàn lại thành available
                        if (nextStatus == BookingStatus.EXPIRED ||
                            nextStatus == BookingStatus.CANCELLED ||
                            nextStatus == BookingStatus.REJECTED
                        ) {
                            if (booking.restaurantId.isNotEmpty() && booking.tableId.isNotEmpty()) {
                                firestore.collection("restaurants").document(booking.restaurantId)
                                    .collection("tables").document(booking.tableId)
                                    .update("status", "available")
                                    .addOnFailureListener { e ->
                                        Log.e(TAG, "Lỗi trả bàn khi hủy đơn: ${e.message}", e)
                                    }
                            }
                        }
                        onResult(Result.success(Unit))
                    }
                    .addOnFailureListener { error ->
                        Log.e(TAG, "Lỗi updateBookingStatus: ${error.message}", error)
                        onResult(Result.failure(error))
                    }
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "Lỗi đọc booking để cập nhật trạng thái: ${error.message}", error)
                onResult(Result.failure(error))
            }
    }

    // Tạo đơn đặt bàn với Firestore Transaction để chống đặt trùng bàn
    fun createBookingWithTransaction(
        booking: Booking,
        onResult: (Result<Booking>) -> Unit
    ) {
        if (booking.restaurantId.isBlank() || booking.tableId.isBlank()) {
            val err = IllegalArgumentException("Thiếu restaurantId hoặc tableId")
            Log.e(TAG, err.message ?: "")
            onResult(Result.failure(err))
            return
        }

        val tableRef = firestore.collection("restaurants").document(booking.restaurantId)
            .collection("tables").document(booking.tableId)
        val bookingId = booking.id.ifEmpty { collection.document().id }
        val bookingRef = collection.document(bookingId)

        firestore.runTransaction { transaction ->
            val tableSnapshot = transaction.get(tableRef)
            if (!tableSnapshot.exists()) {
                throw IllegalStateException("Bàn không tồn tại trên hệ thống")
            }

            val tableStatus = tableSnapshot.getString("status") ?: "available"
            if (tableStatus != "available") {
                throw IllegalStateException("TABLE_ALREADY_BOOKED")
            }

            val now = System.currentTimeMillis()
            val finalBooking = booking.copy(
                id = bookingId,
                status = BookingStatus.PENDING.name,
                createdAt = now
            )

            // Đánh dấu bàn đã được đặt và lưu đơn đặt bàn
            transaction.update(tableRef, "status", "booked")
            transaction.set(bookingRef, finalBooking)
            finalBooking
        }.addOnSuccessListener { createdBooking ->
            onResult(Result.success(createdBooking))
        }.addOnFailureListener { error ->
            Log.e(TAG, "Lỗi transaction createBooking: ${error.message}", error)
            onResult(Result.failure(error))
        }
    }
}
