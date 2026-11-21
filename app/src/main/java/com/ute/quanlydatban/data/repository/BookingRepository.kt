package com.ute.quanlydatban.data.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.ute.quanlydatban.data.model.Booking
import com.ute.quanlydatban.data.model.BookingStatus
import com.ute.quanlydatban.data.model.DiningTable
import com.ute.quanlydatban.data.model.Payment
import com.ute.quanlydatban.data.model.Restaurant
import com.ute.quanlydatban.work.ReminderScheduler
import java.text.SimpleDateFormat
import java.util.Locale

class BookingRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) : BaseRepository<Booking>(firestore.collection("bookings"), Booking::class.java) {

    companion object {
        const val ROLE_CUSTOMER = "customer"
        const val ROLE_OWNER = "owner"
        const val BOOKING_HOLD_TIMEOUT_MS = 10 * 60 * 1000L // 10 phút giữ bàn
        const val REVIEW_WINDOW_MS = 7L * 24 * 60 * 60 * 1000L // 7 ngày
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
     * Lắng nghe danh sách đơn đặt bàn của quán theo thời gian thực (real-time)
     */
    fun listenBookingsForRestaurant(
        restaurantId: String,
        onChange: (Result<List<Booking>>) -> Unit
    ): com.google.firebase.firestore.ListenerRegistration {
        if (restaurantId.isBlank()) {
            onChange(Result.success(emptyList()))
            return collection.whereEqualTo("restaurantId", "__NONE__").addSnapshotListener { _, _ -> }
        }

        return collection
            .whereEqualTo("restaurantId", restaurantId)
            .addSnapshotListener { snap, error ->
                when {
                    error != null -> {
                        Log.e(TAG, "Lỗi listenBookingsForRestaurant($restaurantId): ${error.message}", error)
                        onChange(Result.failure(error))
                    }
                    snap != null -> {
                        val list = snap.documents.map { doc ->
                            val b = doc.toObject(Booking::class.java) ?: Booking()
                            b.copy(id = doc.id)
                        }
                        onChange(Result.success(list))
                    }
                }
            }
    }

    /**
     * Lấy các đơn hàng hoạt động của khách (status = CONFIRMED hoặc CHECKED_IN)
     */
    fun getActiveBookingsForCustomer(
        userId: String,
        onResult: (Result<List<Booking>>) -> Unit
    ) {
        if (userId.isBlank()) {
            onResult(Result.success(emptyList()))
            return
        }

        val activeStatuses = listOf(BookingStatus.CONFIRMED.name, BookingStatus.CHECKED_IN.name)

        collection
            .whereEqualTo("userId", userId)
            .get()
            .addOnSuccessListener { snapshot ->
                val bookings = snapshot.toObjects(Booking::class.java).filter { b ->
                    activeStatuses.contains(b.status)
                }.sortedByDescending { it.createdAt }
                onResult(Result.success(bookings))
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "Lỗi getActiveBookingsForCustomer: ${error.message}", error)
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

                val updatedBooking = booking.copy(
                    id = bookingId,
                    userId = snapshot.getString("userId").orEmpty().ifEmpty { booking.userId },
                    restaurantId = snapshot.getString("restaurantId").orEmpty().ifEmpty { booking.restaurantId },
                    status = nextStatus.name
                )

                collection.document(bookingId).update("status", nextStatus.name)
                    .addOnSuccessListener {
                        Log.e("NotiEvent", "updateBookingStatus success -> bookingId: $bookingId, nextStatus: ${nextStatus.name}")
                        NotificationRepository().createEventNotification(updatedBooking, nextStatus)

                        if (nextStatus == BookingStatus.CONFIRMED) {
                            ReminderScheduler.scheduleReminder(updatedBooking)
                        } else if (nextStatus == BookingStatus.CANCELLED ||
                            nextStatus == BookingStatus.REJECTED ||
                            nextStatus == BookingStatus.EXPIRED ||
                            nextStatus == BookingStatus.NO_SHOW
                        ) {
                            ReminderScheduler.cancelReminder(bookingId)
                        }

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

    /**
     * Xác nhận đơn đặt bàn (WAITING -> CONFIRMED) dùng transaction chống race condition
     */
    fun confirmBookingByOwner(
        bookingId: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        if (bookingId.isBlank()) {
            onResult(Result.failure(IllegalArgumentException("bookingId rỗng")))
            return
        }

        val bookingRef = collection.document(bookingId)
        var capturedBooking: Booking? = null

        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(bookingRef)
            if (!snapshot.exists()) {
                throw IllegalStateException("Không tìm thấy đơn đặt bàn #$bookingId")
            }

            val currentStatusStr = snapshot.getString("status") ?: BookingStatus.PENDING.name
            val currentStatus = BookingStatus.from(currentStatusStr)

            if (!currentStatus.canTransitionTo(BookingStatus.CONFIRMED)) {
                throw IllegalStateException("STATUS_CONFLICT:$currentStatusStr")
            }

            val bookingObj = snapshot.toObject(Booking::class.java) ?: Booking(id = bookingId)
            capturedBooking = bookingObj.copy(
                id = bookingId,
                userId = snapshot.getString("userId").orEmpty().ifEmpty { bookingObj.userId },
                restaurantId = snapshot.getString("restaurantId").orEmpty().ifEmpty { bookingObj.restaurantId },
                status = BookingStatus.CONFIRMED.name
            )

            transaction.update(bookingRef, "status", BookingStatus.CONFIRMED.name)
            null
        }.addOnSuccessListener {
            val bookingToNotify = capturedBooking ?: Booking(id = bookingId, status = BookingStatus.CONFIRMED.name)
            Log.e("NotiEvent", "confirmBookingByOwner success -> bookingId: $bookingId, userId: ${bookingToNotify.userId}")
            NotificationRepository().createEventNotification(bookingToNotify, BookingStatus.CONFIRMED)
            ReminderScheduler.scheduleReminder(bookingToNotify)
            onResult(Result.success(Unit))
        }.addOnFailureListener { error ->
            Log.e(TAG, "Lỗi confirmBookingByOwner: ${error.message}", error)
            onResult(Result.failure(error))
        }
    }

    /**
     * Từ chối đơn đặt bàn (WAITING -> REJECTED)
     * Trả bàn về available và tạo bản ghi hoàn cọc (refund) trong collection payments
     */
    fun rejectBookingByOwner(
        bookingId: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        if (bookingId.isBlank()) {
            onResult(Result.failure(IllegalArgumentException("bookingId rỗng")))
            return
        }

        val bookingRef = collection.document(bookingId)
        var capturedBooking: Booking? = null

        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(bookingRef)
            if (!snapshot.exists()) {
                throw IllegalStateException("Không tìm thấy đơn đặt bàn #$bookingId")
            }

            val currentStatusStr = snapshot.getString("status") ?: BookingStatus.PENDING.name
            val currentStatus = BookingStatus.from(currentStatusStr)

            if (!currentStatus.canTransitionTo(BookingStatus.REJECTED)) {
                throw IllegalStateException("STATUS_CONFLICT:$currentStatusStr")
            }

            val restaurantId = snapshot.getString("restaurantId").orEmpty()
            val tableId = snapshot.getString("tableId").orEmpty()
            val userId = snapshot.getString("userId").orEmpty()
            val depositAmount = snapshot.getLong("depositAmount") ?: 100_000L

            val bookingObj = snapshot.toObject(Booking::class.java) ?: Booking(id = bookingId)
            capturedBooking = bookingObj.copy(
                id = bookingId,
                userId = userId.ifEmpty { bookingObj.userId },
                restaurantId = restaurantId.ifEmpty { bookingObj.restaurantId },
                status = BookingStatus.REJECTED.name
            )

            // 1. Đổi trạng thái booking thành REJECTED
            transaction.update(bookingRef, "status", BookingStatus.REJECTED.name)

            // 2. Trả bàn về trạng thái available
            if (restaurantId.isNotBlank() && tableId.isNotBlank()) {
                val tableRef = firestore.collection("restaurants").document(restaurantId)
                    .collection("tables").document(tableId)
                transaction.update(tableRef, "status", "available")
            }

            // 3. Tạo bản ghi hoàn cọc (refund)
            val refundPaymentId = "PAY_${bookingId}_REFUND"
            val refundRef = firestore.collection("payments").document(refundPaymentId)
            val refundPayment = com.ute.quanlydatban.data.model.Payment(
                id = refundPaymentId,
                bookingId = bookingId,
                userId = userId,
                type = "refund",
                amount = depositAmount,
                method = "vietqr",
                status = "completed",
                paidAt = System.currentTimeMillis()
            )
            transaction.set(refundRef, refundPayment)
            null
        }.addOnSuccessListener {
            val bookingToNotify = capturedBooking ?: Booking(id = bookingId, status = BookingStatus.REJECTED.name)
            Log.e("NotiEvent", "rejectBookingByOwner success -> bookingId: $bookingId, userId: ${bookingToNotify.userId}")
            NotificationRepository().createEventNotification(bookingToNotify, BookingStatus.REJECTED)
            ReminderScheduler.cancelReminder(bookingId)
            onResult(Result.success(Unit))
        }.addOnFailureListener { error ->
            Log.e(TAG, "Lỗi rejectBookingByOwner: ${error.message}", error)
            onResult(Result.failure(error))
        }
    }

    /**
     * Khách hàng hủy đơn đặt bàn:
     * - Áp dụng cho đơn PENDING, WAITING, CONFIRMED
     * - Chặn hủy nếu đơn đã CHECKED_IN, SERVING hoặc quá thời gian hẹn
     * - Trả bàn về trạng thái available
     * - Đổi trạng thái booking thành CANCELLED
     * - Cập nhật trạng thái hoàn tiền cọc trong collection payments
     */
    fun cancelBookingByCustomer(
        bookingId: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        if (bookingId.isBlank()) {
            onResult(Result.failure(IllegalArgumentException("bookingId không được để trống")))
            return
        }

        val bookingRef = collection.document(bookingId)

        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(bookingRef)
            if (!snapshot.exists()) {
                throw IllegalStateException("Không tìm thấy đơn đặt bàn #$bookingId")
            }

            val booking = snapshot.toObject(Booking::class.java)
                ?: throw IllegalStateException("Không đọc được dữ liệu đơn đặt bàn")

            val currentStatus = BookingStatus.from(booking.status)
            if (currentStatus == BookingStatus.CHECKED_IN || currentStatus == BookingStatus.SERVING) {
                throw IllegalStateException("CANNOT_CANCEL_CHECKED_IN")
            }
            if (currentStatus == BookingStatus.CANCELLED ||
                currentStatus == BookingStatus.REJECTED ||
                currentStatus == BookingStatus.EXPIRED ||
                currentStatus == BookingStatus.NO_SHOW ||
                currentStatus == BookingStatus.COMPLETED
            ) {
                throw IllegalStateException("ALREADY_FINISHED")
            }

            // Kiểm tra xem đơn đã qua thời gian hẹn chưa
            if (isPastSchedule(booking.date, booking.time)) {
                throw IllegalStateException("PAST_APPOINTMENT_TIME")
            }

            // 1. Cập nhật trạng thái booking thành CANCELLED
            transaction.update(bookingRef, "status", BookingStatus.CANCELLED.name)

            // 2. Trả bàn về available
            if (booking.restaurantId.isNotBlank() && booking.tableId.isNotBlank()) {
                val tableRef = firestore.collection("restaurants").document(booking.restaurantId)
                    .collection("tables").document(booking.tableId)
                transaction.update(tableRef, "status", "available")
            }

            // 3. Hoàn tiền cọc trong collection payments
            val refundPaymentId = "PAY_${bookingId}_REFUND"
            val refundRef = firestore.collection("payments").document(refundPaymentId)
            val refundPayment = Payment(
                id = refundPaymentId,
                bookingId = bookingId,
                userId = booking.userId,
                type = "refund",
                amount = booking.depositAmount,
                method = "vietqr",
                status = "completed",
                paidAt = System.currentTimeMillis()
            )
            transaction.set(refundRef, refundPayment)
            null
        }.addOnSuccessListener {
            // Cập nhật thêm khoản cọc gốc thành "refunded" nếu có
            firestore.collection("payments")
                .whereEqualTo("bookingId", bookingId)
                .whereEqualTo("type", "deposit")
                .get()
                .addOnSuccessListener { paySnaps ->
                    for (doc in paySnaps.documents) {
                        doc.reference.update("status", "refunded")
                    }
                }
            Log.i(TAG, "Khách đã hủy thành công đơn #$bookingId")
            ReminderScheduler.cancelReminder(bookingId)
            onResult(Result.success(Unit))
        }.addOnFailureListener { error ->
            Log.e(TAG, "Lỗi cancelBookingByCustomer: ${error.message}", error)
            onResult(Result.failure(error))
        }
    }

    /**
     * Khách hàng đổi ngày, giờ hoặc bàn mới:
     * - Kiểm tra đơn chưa CHECKED_IN, SERVING, hoặc quá giờ hẹn
     * - Kiểm tra trùng lịch trên Firestore (bàn đó đã có đơn CONFIRMED/WAITING chưa)
     * - Đổi bàn (giải phóng bàn cũ, giữ bàn mới) và cập nhật đơn
     */
    fun rescheduleBooking(
        bookingId: String,
        newDate: String,
        newTime: String,
        newTableId: String?,
        onResult: (Result<Unit>) -> Unit
    ) {
        if (bookingId.isBlank() || newDate.isBlank() || newTime.isBlank()) {
            onResult(Result.failure(IllegalArgumentException("Thiếu thông tin đổi lịch hẹn")))
            return
        }

        if (isPastSchedule(newDate, newTime)) {
            onResult(Result.failure(IllegalArgumentException("PAST_NEW_SCHEDULE")))
            return
        }

        collection.document(bookingId).get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.exists()) {
                    onResult(Result.failure(IllegalStateException("Không tìm thấy đơn #$bookingId")))
                    return@addOnSuccessListener
                }

                val booking = snapshot.toObject(Booking::class.java)
                if (booking == null) {
                    onResult(Result.failure(IllegalStateException("Lỗi dữ liệu booking")))
                    return@addOnSuccessListener
                }

                val currentStatus = BookingStatus.from(booking.status)
                if (currentStatus == BookingStatus.CHECKED_IN || currentStatus == BookingStatus.SERVING) {
                    onResult(Result.failure(IllegalStateException("CANNOT_MODIFY_CHECKED_IN")))
                    return@addOnSuccessListener
                }
                if (currentStatus == BookingStatus.CANCELLED ||
                    currentStatus == BookingStatus.REJECTED ||
                    currentStatus == BookingStatus.EXPIRED ||
                    currentStatus == BookingStatus.NO_SHOW ||
                    currentStatus == BookingStatus.COMPLETED
                ) {
                    onResult(Result.failure(IllegalStateException("ALREADY_FINISHED")))
                    return@addOnSuccessListener
                }

                if (isPastSchedule(booking.date, booking.time)) {
                    onResult(Result.failure(IllegalStateException("PAST_APPOINTMENT_TIME")))
                    return@addOnSuccessListener
                }

                val targetTableId = if (!newTableId.isNullOrBlank()) newTableId else booking.tableId

                // Kiểm tra trùng lịch trên Firestore
                collection
                    .whereEqualTo("restaurantId", booking.restaurantId)
                    .whereEqualTo("tableId", targetTableId)
                    .whereEqualTo("date", newDate)
                    .whereEqualTo("time", newTime)
                    .get()
                    .addOnSuccessListener { querySnapshot ->
                        val hasConflict = querySnapshot.documents.any { doc ->
                            if (doc.id == bookingId) return@any false
                            val b = doc.toObject(Booking::class.java) ?: return@any false
                            val st = BookingStatus.from(b.status)
                            st != BookingStatus.CANCELLED &&
                                st != BookingStatus.REJECTED &&
                                st != BookingStatus.EXPIRED &&
                                st != BookingStatus.NO_SHOW
                        }

                        if (hasConflict) {
                            onResult(Result.failure(IllegalStateException("TABLE_CONFLICT")))
                            return@addOnSuccessListener
                        }

                        // Thực hiện đổi lịch
                        val bookingRef = collection.document(bookingId)
                        val updates = mutableMapOf<String, Any>(
                            "date" to newDate,
                            "time" to newTime,
                            "tableId" to targetTableId
                        )

                        firestore.runTransaction { tx ->
                            // Nếu đổi bàn khác, cập nhật bàn cũ thành available và bàn mới thành booked
                            if (targetTableId != booking.tableId && booking.restaurantId.isNotBlank()) {
                                if (booking.tableId.isNotBlank()) {
                                    val oldTableRef = firestore.collection("restaurants")
                                        .document(booking.restaurantId)
                                        .collection("tables").document(booking.tableId)
                                    tx.update(oldTableRef, "status", "available")
                                }
                                val newTableRef = firestore.collection("restaurants")
                                    .document(booking.restaurantId)
                                    .collection("tables").document(targetTableId)
                                tx.update(newTableRef, "status", "booked")
                            }
                            tx.update(bookingRef, updates)
                            null
                        }.addOnSuccessListener {
                            Log.i(TAG, "Đã đổi lịch thành công cho đơn #$bookingId")
                            ReminderScheduler.cancelReminder(bookingId)
                            if (currentStatus == BookingStatus.CONFIRMED) {
                                val updatedBooking = booking.copy(
                                    id = bookingId,
                                    date = newDate,
                                    time = newTime,
                                    tableId = targetTableId
                                )
                                ReminderScheduler.scheduleReminder(updatedBooking)
                            }
                            onResult(Result.success(Unit))
                        }.addOnFailureListener { err ->
                            Log.e(TAG, "Lỗi transaction reschedule: ${err.message}", err)
                            onResult(Result.failure(err))
                        }
                    }
                    .addOnFailureListener { err ->
                        Log.e(TAG, "Lỗi kiểm tra trùng lịch: ${err.message}", err)
                        onResult(Result.failure(err))
                    }
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "Lỗi đọc đơn đổi lịch: ${error.message}", error)
                onResult(Result.failure(error))
            }
    }

    /**
     * Lấy danh sách lịch sử đặt bàn của khách hàng theo userId
     */
    fun getUserBookings(
        userId: String,
        onResult: (Result<List<Booking>>) -> Unit
    ) {
        if (userId.isBlank()) {
            onResult(Result.success(emptyList()))
            return
        }

        cleanupExpiredPaidBookings(userId)

        collection.whereEqualTo("userId", userId).get()
            .addOnSuccessListener { snapshot ->
                val bookings = snapshot.toObjects(Booking::class.java)
                onResult(Result.success(bookings))
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "Lỗi getUserBookings: ${error.message}", error)
                onResult(Result.failure(error))
            }
    }

    private fun cleanupExpiredPaidBookings(userId: String) {
        if (userId.isBlank()) return
        val now = System.currentTimeMillis()
        val expiredThreshold = now - REVIEW_WINDOW_MS

        collection
            .whereEqualTo("userId", userId)
            .whereEqualTo("status", BookingStatus.PAID.name)
            .get()
            .addOnSuccessListener { snapshot ->
                for (doc in snapshot.documents) {
                    val booking = doc.toObject(Booking::class.java)?.copy(id = doc.id) ?: continue
                    val baseTime = if (booking.checkedInAt > 0) booking.checkedInAt else booking.createdAt
                    if (baseTime in 1..expiredThreshold) {
                        updateBookingStatus(booking.id, BookingStatus.COMPLETED) { result ->
                            result.onSuccess {
                                Log.i(TAG, "Đã tự động chuyển đơn PAID quá hạn đánh giá sang COMPLETED: #${booking.id}")
                            }
                        }
                    }
                }
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "Lỗi cleanupExpiredPaidBookings: ${error.message}", error)
            }
    }

    /**
     * Kiểm tra thời gian lịch hẹn đã trôi qua so với hiện tại hay chưa
     */
    fun isPastSchedule(dateStr: String, timeStr: String): Boolean {
        if (dateStr.isBlank() || timeStr.isBlank()) return false
        val formats = listOf("dd/MM/yyyy HH:mm", "yyyy-MM-dd HH:mm", "d/M/yyyy H:m")
        for (pattern in formats) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.getDefault())
                val parsed = sdf.parse("$dateStr $timeStr")
                if (parsed != null) {
                    return System.currentTimeMillis() > parsed.time
                }
            } catch (_: Exception) {}
        }
        return false
    }
}
