package com.ute.quanlydatban.data.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.ute.quanlydatban.data.model.Booking
import com.ute.quanlydatban.data.model.BookingStatus
import com.ute.quanlydatban.data.model.DiningTable
import com.ute.quanlydatban.data.model.OrderItem
import com.ute.quanlydatban.data.model.Payment
import com.ute.quanlydatban.data.model.Restaurant
import com.ute.quanlydatban.data.model.User

data class CheckoutData(
    val booking: Booking,
    val restaurant: Restaurant?,
    val table: DiningTable?,
    val customerUser: User?,
    val orderItems: List<OrderItem>,
    val totalDishPrice: Long,
    val depositAmount: Long,
    val voucherDiscount: Long = 0L,
    val pointsDiscount: Long = 0L,
    val finalAmount: Long
)

class CheckoutRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    companion object {
        private const val TAG = "CheckoutRepository"
        const val DEFAULT_DEPOSIT_AMOUNT = 100_000L
    }

    /**
     * Tìm kiếm đơn đặt bàn theo mã vé quét được hoặc mã nhập tay:
     * Hỗ trợ tìm theo id doc, booking.qrCode, hoặc chuỗi BOOKTABLE_TICKET:...
     */
    fun findBookingByTicket(
        rawTicket: String,
        onResult: (Result<Booking?>) -> Unit
    ) {
        val cleanInput = rawTicket.trim()
        if (cleanInput.isBlank()) {
            onResult(Result.success(null))
            return
        }

        val stripped = cleanInput.removePrefix("BOOKTABLE_TICKET:").trim()
        val strippedBt = stripped.removePrefix("BT").removePrefix("#BT-").trim()

        // 1. Thử tìm trực tiếp theo docId
        val directRef = firestore.collection("bookings").document(stripped)
        directRef.get().addOnSuccessListener { snap ->
            if (snap.exists()) {
                val b = snap.toObject(Booking::class.java)?.copy(id = snap.id)
                onResult(Result.success(b))
                return@addOnSuccessListener
            }

            // 2. Thử tìm theo docId bỏ prefix BT
            if (strippedBt.isNotBlank() && strippedBt != stripped) {
                firestore.collection("bookings").document(strippedBt).get()
                    .addOnSuccessListener { btSnap ->
                        if (btSnap.exists()) {
                            val b = btSnap.toObject(Booking::class.java)?.copy(id = btSnap.id)
                            onResult(Result.success(b))
                            return@addOnSuccessListener
                        }
                        searchByQrCodeField(cleanInput, stripped, strippedBt, onResult)
                    }
                    .addOnFailureListener {
                        searchByQrCodeField(cleanInput, stripped, strippedBt, onResult)
                    }
            } else {
                searchByQrCodeField(cleanInput, stripped, strippedBt, onResult)
            }
        }.addOnFailureListener {
            searchByQrCodeField(cleanInput, stripped, strippedBt, onResult)
        }
    }

    private fun searchByQrCodeField(
        original: String,
        stripped: String,
        strippedBt: String,
        onResult: (Result<Booking?>) -> Unit
    ) {
        firestore.collection("bookings")
            .whereEqualTo("qrCode", original)
            .limit(1)
            .get()
            .addOnSuccessListener { snap ->
                if (!snap.isEmpty) {
                    val b = snap.documents.first().toObject(Booking::class.java)?.copy(id = snap.documents.first().id)
                    onResult(Result.success(b))
                    return@addOnSuccessListener
                }

                // Thử tìm theo trường qrCode = stripped
                firestore.collection("bookings")
                    .whereEqualTo("qrCode", stripped)
                    .limit(1)
                    .get()
                    .addOnSuccessListener { snap2 ->
                        if (!snap2.isEmpty) {
                            val b = snap2.documents.first().toObject(Booking::class.java)?.copy(id = snap2.documents.first().id)
                            onResult(Result.success(b))
                        } else {
                            // Thử tìm theo id doc kết thúc bằng strippedBt
                            findBookingFuzzy(strippedBt, onResult)
                        }
                    }
                    .addOnFailureListener { err ->
                        Log.e(TAG, "Lỗi searchByQrCodeField: ${err.message}", err)
                        onResult(Result.failure(err))
                    }
            }
            .addOnFailureListener { err ->
                Log.e(TAG, "Lỗi searchByQrCodeField: ${err.message}", err)
                onResult(Result.failure(err))
            }
    }

    private fun findBookingFuzzy(
        keyword: String,
        onResult: (Result<Booking?>) -> Unit
    ) {
        if (keyword.isBlank()) {
            onResult(Result.success(null))
            return
        }

        firestore.collection("bookings").limit(50).get()
            .addOnSuccessListener { snap ->
                val match = snap.documents.firstOrNull { doc ->
                    val b = doc.toObject(Booking::class.java) ?: return@firstOrNull false
                    doc.id.contains(keyword, ignoreCase = true) ||
                        b.qrCode.contains(keyword, ignoreCase = true) ||
                        "BT${doc.id}".contains(keyword, ignoreCase = true)
                }?.let { doc ->
                    doc.toObject(Booking::class.java)?.copy(id = doc.id)
                }
                onResult(Result.success(match))
            }
            .addOnFailureListener { err ->
                onResult(Result.failure(err))
            }
    }

    /**
     * Chuyển trạng thái đơn từ CHECKED_IN sang SERVING khi quét vé:
     * - Kiểm tra đúng quán
     * - Đơn phải ở trạng thái CHECKED_IN
     */
    fun transitionToServing(
        bookingId: String,
        currentRestaurantId: String,
        onResult: (Result<Booking>) -> Unit
    ) {
        if (bookingId.isBlank()) {
            onResult(Result.failure(IllegalArgumentException("bookingId không được để trống")))
            return
        }

        val bookingRef = firestore.collection("bookings").document(bookingId)

        firestore.runTransaction { tx ->
            val snap = tx.get(bookingRef)
            if (!snap.exists()) {
                throw IllegalStateException("BOOKING_NOT_FOUND")
            }

            val booking = snap.toObject(Booking::class.java)?.copy(id = snap.id)
                ?: throw IllegalStateException("BOOKING_NOT_FOUND")

            if (currentRestaurantId.isNotBlank() && booking.restaurantId.isNotBlank() &&
                booking.restaurantId != currentRestaurantId
            ) {
                throw IllegalStateException("WRONG_RESTAURANT")
            }

            val status = BookingStatus.from(booking.status)
            when (status) {
                BookingStatus.PENDING, BookingStatus.CONFIRMED, BookingStatus.WAITING -> {
                    throw IllegalStateException("NOT_CHECKED_IN")
                }
                BookingStatus.SERVING -> {
                    throw IllegalStateException("ALREADY_SERVING")
                }
                BookingStatus.PAID, BookingStatus.COMPLETED -> {
                    throw IllegalStateException("ALREADY_PAID")
                }
                BookingStatus.CANCELLED, BookingStatus.REJECTED, BookingStatus.EXPIRED, BookingStatus.NO_SHOW -> {
                    throw IllegalStateException("BOOKING_CANCELLED")
                }
                BookingStatus.CHECKED_IN -> {
                    tx.update(bookingRef, "status", BookingStatus.SERVING.name)
                    booking.copy(status = BookingStatus.SERVING.name)
                }
            }
        }.addOnSuccessListener { updatedBooking ->
            Log.i(TAG, "Đã chuyển đơn #${bookingId} sang SERVING")
            onResult(Result.success(updatedBooking))
        }.addOnFailureListener { error ->
            Log.e(TAG, "Lỗi transitionToServing: ${error.message}", error)
            onResult(Result.failure(error))
        }
    }

    /**
     * Lấy đầy đủ thông tin thanh toán:
     * - Thông tin đơn đặt bàn, nhà hàng, bàn, khách hàng
     * - Toàn bộ danh sách orderItems (pre và table)
     * - Tính toán tiền cọc, tổng tiền món và tiền còn lại (kiểu Long)
     */
    fun getCheckoutData(
        bookingId: String,
        onResult: (Result<CheckoutData>) -> Unit
    ) {
        if (bookingId.isBlank()) {
            onResult(Result.failure(IllegalArgumentException("bookingId không được để trống")))
            return
        }

        firestore.collection("bookings").document(bookingId).get()
            .addOnSuccessListener { bookingSnap ->
                if (!bookingSnap.exists()) {
                    onResult(Result.failure(IllegalStateException("BOOKING_NOT_FOUND")))
                    return@addOnSuccessListener
                }

                val booking = bookingSnap.toObject(Booking::class.java)?.copy(id = bookingSnap.id)
                    ?: run {
                        onResult(Result.failure(IllegalStateException("BOOKING_PARSE_ERROR")))
                        return@addOnSuccessListener
                    }

                // Lấy song song thông tin món, quán, bàn, khách
                firestore.collection("orderItems")
                    .whereEqualTo("bookingId", bookingId)
                    .get()
                    .addOnSuccessListener { itemsSnap ->
                        val orderItems = itemsSnap.documents.mapNotNull {
                            it.toObject(OrderItem::class.java)?.copy(id = it.id)
                        }

                        // Tính tổng tiền món (Long)
                        var totalDishes = 0L
                        for (item in orderItems) {
                            totalDishes += (item.qty * item.unitPrice)
                        }

                        val deposit = if (booking.depositAmount > 0) booking.depositAmount else DEFAULT_DEPOSIT_AMOUNT
                        val finalAmt = maxOf(0L, totalDishes - deposit)

                        // Lấy nhà hàng, bàn và khách
                        loadExtraEntities(booking) { res, table, user ->
                            val checkoutData = CheckoutData(
                                booking = booking,
                                restaurant = res,
                                table = table,
                                customerUser = user,
                                orderItems = orderItems,
                                totalDishPrice = totalDishes,
                                depositAmount = deposit,
                                voucherDiscount = 0L,
                                pointsDiscount = 0L,
                                finalAmount = finalAmt
                            )
                            onResult(Result.success(checkoutData))
                        }
                    }
                    .addOnFailureListener { err ->
                        Log.e(TAG, "Lỗi getOrderItems: ${err.message}", err)
                        onResult(Result.failure(err))
                    }
            }
            .addOnFailureListener { err ->
                Log.e(TAG, "Lỗi đọc booking: ${err.message}", err)
                onResult(Result.failure(err))
            }
    }

    private fun loadExtraEntities(
        booking: Booking,
        onLoaded: (Restaurant?, DiningTable?, User?) -> Unit
    ) {
        var restaurant: Restaurant? = null
        var table: DiningTable? = null
        var customer: User? = null

        val rRef = if (booking.restaurantId.isNotBlank())
            firestore.collection("restaurants").document(booking.restaurantId) else null
        val tRef = if (booking.restaurantId.isNotBlank() && booking.tableId.isNotBlank())
            firestore.collection("restaurants").document(booking.restaurantId).collection("tables").document(booking.tableId) else null
        val uRef = if (booking.userId.isNotBlank())
            firestore.collection("users").document(booking.userId) else null

        // Tải đơn giản: nếu có doc thì đọc
        rRef?.get()?.addOnSuccessListener { snap ->
            if (snap.exists()) restaurant = snap.toObject(Restaurant::class.java)
            tRef?.get()?.addOnSuccessListener { tSnap ->
                if (tSnap.exists()) table = tSnap.toObject(DiningTable::class.java)?.copy(id = tSnap.id)
                uRef?.get()?.addOnSuccessListener { uSnap ->
                    if (uSnap.exists()) customer = uSnap.toObject(User::class.java)
                    onLoaded(restaurant, table, customer)
                }?.addOnFailureListener { onLoaded(restaurant, table, customer) }
            }?.addOnFailureListener {
                uRef?.get()?.addOnSuccessListener { uSnap ->
                    if (uSnap.exists()) customer = uSnap.toObject(User::class.java)
                    onLoaded(restaurant, table, customer)
                }?.addOnFailureListener { onLoaded(restaurant, table, customer) }
            }
        }?.addOnFailureListener {
            onLoaded(restaurant, table, customer)
        } ?: run {
            onLoaded(restaurant, table, customer)
        }
    }

    /**
     * Xác nhận thanh toán hóa đơn Checkout:
     * - Chống double-tap: Sử dụng Transaction
     * - Tạo 1 bản ghi duy nhất trong payments (id = PAY_{bookingId}_CHECKOUT, status = SUCCESS)
     * - Cập nhật trạng thái booking: SERVING -> PAID
     */
    fun confirmCheckout(
        bookingId: String,
        method: String,
        finalAmount: Long,
        onResult: (Result<Payment>) -> Unit
    ) {
        if (bookingId.isBlank()) {
            onResult(Result.failure(IllegalArgumentException("bookingId không được để trống")))
            return
        }

        val bookingRef = firestore.collection("bookings").document(bookingId)
        val paymentId = "PAY_${bookingId}_CHECKOUT"
        val paymentRef = firestore.collection("payments").document(paymentId)

        firestore.runTransaction { tx ->
            val bSnap = tx.get(bookingRef)
            if (!bSnap.exists()) {
                throw IllegalStateException("BOOKING_NOT_FOUND")
            }

            val booking = bSnap.toObject(Booking::class.java)?.copy(id = bSnap.id)
                ?: throw IllegalStateException("BOOKING_NOT_FOUND")

            // Kiểm tra trạng thái đã thanh toán trước đó chưa (idempotent chống thanh toán 2 lần)
            val currentStatus = BookingStatus.from(booking.status)
            if (currentStatus == BookingStatus.PAID || currentStatus == BookingStatus.COMPLETED) {
                val existingPaySnap = tx.get(paymentRef)
                if (existingPaySnap.exists()) {
                    return@runTransaction existingPaySnap.toObject(Payment::class.java)!!
                }
            }

            if (currentStatus != BookingStatus.SERVING && currentStatus != BookingStatus.CHECKED_IN) {
                throw IllegalStateException("STATUS_CONFLICT")
            }

            val paySnap = tx.get(paymentRef)
            if (paySnap.exists()) {
                return@runTransaction paySnap.toObject(Payment::class.java)!!
            }

            val now = System.currentTimeMillis()
            val payment = Payment(
                id = paymentId,
                bookingId = bookingId,
                userId = booking.userId,
                type = "checkout",
                amount = maxOf(0L, finalAmount),
                method = method,
                status = "SUCCESS",
                paidAt = now
            )

            tx.set(paymentRef, payment)
            tx.update(bookingRef, "status", BookingStatus.PAID.name)
            payment
        }.addOnSuccessListener { payment ->
            Log.i(TAG, "Đã thanh toán checkout thành công đơn #${bookingId}")
            onResult(Result.success(payment))
        }.addOnFailureListener { error ->
            Log.e(TAG, "Lỗi confirmCheckout: ${error.message}", error)
            onResult(Result.failure(error))
        }
    }
}
