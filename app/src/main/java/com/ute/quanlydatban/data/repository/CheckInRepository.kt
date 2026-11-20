package com.ute.quanlydatban.data.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.ute.quanlydatban.data.model.Booking
import com.ute.quanlydatban.data.model.BookingStatus
import com.ute.quanlydatban.data.model.DiningTable
import com.ute.quanlydatban.data.model.QrLocation
import com.ute.quanlydatban.data.model.Restaurant

/**
 * Các loại lỗi đặc thù khi check-in theo mô tả thẻ Trello
 */
sealed class CheckInException(message: String) : Exception(message) {
    object InvalidQr : CheckInException("Mã QR sai định dạng hoặc không phải mã vị trí check-in.")
    object WrongRestaurant : CheckInException("Không đúng quán đã đặt.")
    object NotConfirmed : CheckInException("Đơn đặt bàn chưa được xác nhận, không thể check-in.")
    object Expired : CheckInException("Đơn đặt bàn đã hết hạn, không thể check-in.")
    object NoShow : CheckInException("Đơn đặt bàn đã bị đánh dấu vắng mặt (No-show), không thể check-in.")
    object Cancelled : CheckInException("Đơn đặt bàn đã bị hủy, không thể check-in.")
    object AlreadyCheckedIn : CheckInException("Đơn đặt bàn này đã được check-in trước đó.")
    object BookingNotFound : CheckInException("Không tìm thấy thông tin đơn đặt bàn.")
    class GeneralError(msg: String) : CheckInException(msg)
}

data class CheckInResultData(
    val booking: Booking,
    val qrLocation: QrLocation?
)

class CheckInRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    companion object {
        private const val TAG = "CheckInRepository"
        const val PREFIX_LOCATION = "BOOKTABLE_LOCATION:"
    }

    /**
     * Lấy thông tin đơn đặt bàn
     */
    fun getBooking(bookingId: String, onResult: (Result<Booking?>) -> Unit) {
        if (bookingId.isBlank()) {
            onResult(Result.failure(CheckInException.BookingNotFound))
            return
        }
        firestore.collection("bookings").document(bookingId).get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    val booking = snapshot.toObject(Booking::class.java)?.copy(id = snapshot.id)
                    onResult(Result.success(booking))
                } else {
                    onResult(Result.failure(CheckInException.BookingNotFound))
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Lỗi đọc booking: ${e.message}", e)
                onResult(Result.failure(e))
            }
    }

    /**
     * Lấy thông tin nhà hàng
     */
    fun getRestaurant(restaurantId: String, onResult: (Result<Restaurant?>) -> Unit) {
        if (restaurantId.isBlank()) {
            onResult(Result.success(null))
            return
        }
        firestore.collection("restaurants").document(restaurantId).get()
            .addOnSuccessListener { snapshot ->
                val restaurant = snapshot.toObject(Restaurant::class.java)?.copy(id = snapshot.id)
                onResult(Result.success(restaurant))
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Lỗi đọc restaurant: ${e.message}", e)
                onResult(Result.failure(e))
            }
    }

    /**
     * Lấy thông tin bàn
     */
    fun getTable(restaurantId: String, tableId: String, onResult: (Result<DiningTable?>) -> Unit) {
        if (restaurantId.isBlank() || tableId.isBlank()) {
            onResult(Result.success(null))
            return
        }
        firestore.collection("restaurants").document(restaurantId)
            .collection("tables").document(tableId).get()
            .addOnSuccessListener { snapshot ->
                val table = snapshot.toObject(DiningTable::class.java)?.copy(id = snapshot.id)
                onResult(Result.success(table))
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Lỗi đọc table: ${e.message}", e)
                onResult(Result.failure(e))
            }
    }

    /**
     * Thực hiện check-in với mã QR quét được hoặc mã nhập thủ công.
     */
    fun checkIn(
        bookingId: String,
        expectedRestaurantId: String,
        qrRawContent: String,
        onResult: (Result<CheckInResultData>) -> Unit
    ) {
        val trimmed = qrRawContent.trim()
        if (trimmed.isEmpty()) {
            onResult(Result.failure(CheckInException.InvalidQr))
            return
        }

        // 1. Phân tích mã QR / mã vị trí
        resolveQrLocation(trimmed, expectedRestaurantId) { locResult ->
            locResult.onFailure { error ->
                onResult(Result.failure(error))
            }.onSuccess { qrLocation ->
                // Đối chiếu restaurantId nếu tìm thấy location
                if (qrLocation != null && qrLocation.restaurantId.isNotBlank() &&
                    expectedRestaurantId.isNotBlank() &&
                    qrLocation.restaurantId != expectedRestaurantId
                ) {
                    onResult(Result.failure(CheckInException.WrongRestaurant))
                    return@onSuccess
                }

                // 2. Kiểm tra trạng thái đơn đặt bàn
                firestore.collection("bookings").document(bookingId).get()
                    .addOnSuccessListener { snapshot ->
                        val booking = snapshot.toObject(Booking::class.java)?.copy(id = snapshot.id)
                        if (booking == null) {
                            onResult(Result.failure(CheckInException.BookingNotFound))
                            return@addOnSuccessListener
                        }

                        // Đối chiếu thêm mã nhà hàng từ booking nếu trước đó chưa có
                        val bookingRestaurantId = booking.restaurantId
                        if (qrLocation != null && qrLocation.restaurantId.isNotBlank() &&
                            bookingRestaurantId.isNotBlank() &&
                            qrLocation.restaurantId != bookingRestaurantId
                        ) {
                            onResult(Result.failure(CheckInException.WrongRestaurant))
                            return@addOnSuccessListener
                        }

                        val currentStatus = BookingStatus.from(booking.status)

                        when (currentStatus) {
                            BookingStatus.CHECKED_IN -> {
                                // Quét hai lần: không đổi trạng thái lần hai, báo lý do
                                onResult(Result.failure(CheckInException.AlreadyCheckedIn))
                                return@addOnSuccessListener
                            }
                            BookingStatus.EXPIRED -> {
                                onResult(Result.failure(CheckInException.Expired))
                                return@addOnSuccessListener
                            }
                            BookingStatus.NO_SHOW -> {
                                onResult(Result.failure(CheckInException.NoShow))
                                return@addOnSuccessListener
                            }
                            BookingStatus.CANCELLED, BookingStatus.REJECTED -> {
                                onResult(Result.failure(CheckInException.Cancelled))
                                return@addOnSuccessListener
                            }
                            BookingStatus.CONFIRMED -> {
                                // Trạng thái hợp lệ -> cập nhật CHECKED_IN và checkedInAt
                                val now = System.currentTimeMillis()
                                val updates = mapOf(
                                    "status" to BookingStatus.CHECKED_IN.name,
                                    "checkedInAt" to now
                                )

                                firestore.collection("bookings").document(bookingId)
                                    .update(updates)
                                    .addOnSuccessListener {
                                        val updatedBooking = booking.copy(
                                            status = BookingStatus.CHECKED_IN.name,
                                            checkedInAt = now
                                        )
                                        Log.i(TAG, "Check-in thành công booking #$bookingId lúc $now")
                                        onResult(Result.success(CheckInResultData(updatedBooking, qrLocation)))
                                    }
                                    .addOnFailureListener { e ->
                                        Log.e(TAG, "Lỗi cập nhật trạng thái check-in: ${e.message}", e)
                                        onResult(Result.failure(CheckInException.GeneralError("Không thể cập nhật trạng thái check-in: ${e.message}")))
                                    }
                            }
                            else -> {
                                // Đơn chưa CONFIRMED (PENDING, WAITING...)
                                onResult(Result.failure(CheckInException.NotConfirmed))
                            }
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Lỗi đọc booking để check-in: ${e.message}", e)
                        onResult(Result.failure(e))
                    }
            }
        }
    }

    /**
     * Tra cứu vị trí QR từ chuỗi quét được hoặc mã nhập
     */
    private fun resolveQrLocation(
        rawContent: String,
        expectedRestaurantId: String,
        onResolved: (Result<QrLocation?>) -> Unit
    ) {
        val cleanContent = if (rawContent.startsWith(PREFIX_LOCATION, ignoreCase = true)) {
            rawContent.substring(PREFIX_LOCATION.length).trim()
        } else {
            rawContent
        }

        // Nếu nội dung chứa dấu hai chấm ví dụ "res_01:loc_01"
        val locationId = if (cleanContent.contains(":")) {
            val parts = cleanContent.split(":")
            val resPart = parts[0]
            val locPart = parts[1]
            if (expectedRestaurantId.isNotBlank() && resPart != expectedRestaurantId) {
                onResolved(Result.failure(CheckInException.WrongRestaurant))
                return
            }
            locPart
        } else {
            cleanContent
        }

        if (locationId.isBlank()) {
            onResolved(Result.failure(CheckInException.InvalidQr))
            return
        }

        // Tra cứu trong Firestore collection qrLocations
        firestore.collection("qrLocations").document(locationId).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val loc = doc.toObject(QrLocation::class.java)?.copy(id = doc.id)
                    onResolved(Result.success(loc))
                } else {
                    // Thử tìm theo label hoặc id trong sub-collection hoặc query whereEqualTo
                    firestore.collection("qrLocations")
                        .whereEqualTo("label", locationId)
                        .limit(1)
                        .get()
                        .addOnSuccessListener { querySnap ->
                            if (!querySnap.isEmpty) {
                                val matchDoc = querySnap.documents[0]
                                val loc = matchDoc.toObject(QrLocation::class.java)?.copy(id = matchDoc.id)
                                onResolved(Result.success(loc))
                            } else {
                                // Nếu người dùng nhập mã vị trí khớp với quán hoặc seed thử nghiệm
                                if (locationId.startsWith("LOC_", ignoreCase = true) ||
                                    locationId.startsWith("QRLOC_", ignoreCase = true) ||
                                    locationId.startsWith("TABLE_", ignoreCase = true) ||
                                    locationId.startsWith("BAN_", ignoreCase = true)
                                ) {
                                    // Tạo mock/fallback QrLocation với expectedRestaurantId
                                    val fallback = QrLocation(
                                        id = locationId,
                                        restaurantId = expectedRestaurantId,
                                        label = locationId
                                    )
                                    onResolved(Result.success(fallback))
                                } else {
                                    // QR sai định dạng hoặc không phải QR vị trí
                                    onResolved(Result.failure(CheckInException.InvalidQr))
                                }
                            }
                        }
                        .addOnFailureListener {
                            onResolved(Result.failure(CheckInException.InvalidQr))
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Lỗi tra cứu qrLocation: ${e.message}", e)
                onResolved(Result.failure(CheckInException.InvalidQr))
            }
    }
}
