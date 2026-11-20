package com.ute.quanlydatban.data.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.ute.quanlydatban.data.model.Booking
import com.ute.quanlydatban.data.model.BookingGuest
import com.ute.quanlydatban.data.model.BookingStatus
import com.ute.quanlydatban.data.model.OrderItem
import com.ute.quanlydatban.data.model.Payment

object SplitBillCalculator {

    fun calculateEqualSplit(totalPayable: Long, numPersons: Int): List<Long> {
        if (numPersons < 2 || totalPayable <= 0L) return emptyList()
        val baseShare = totalPayable / numPersons
        val remainder = totalPayable % numPersons
        val result = MutableList(numPersons) { baseShare }
        result[0] = result[0] + remainder
        return result
    }

    fun calculateItemSplit(
        orderItems: List<OrderItem>,
        assignments: Map<String, Int>,
        numPersons: Int,
        totalPayable: Long
    ): Result<List<Long>> {
        if (numPersons < 2) {
            return Result.failure(IllegalArgumentException("Cần ít nhất 2 người để chia hóa đơn."))
        }

        for (item in orderItems) {
            val assigned = assignments[item.id]
            if (assigned == null || assigned !in 0 until numPersons) {
                return Result.failure(IllegalStateException("Món \"${item.name}\" chưa được gán cho ai."))
            }
        }

        val personRawTotals = MutableList(numPersons) { 0L }
        for (item in orderItems) {
            val personIdx = assignments[item.id] ?: 0
            val itemPrice = item.qty * item.unitPrice
            personRawTotals[personIdx] = personRawTotals[personIdx] + itemPrice
        }

        val totalDishes = personRawTotals.sum()
        if (totalDishes == 0L && totalPayable > 0L) {
            return Result.success(calculateEqualSplit(totalPayable, numPersons))
        }

        if (totalDishes == totalPayable) {
            return Result.success(personRawTotals)
        }

        val discount = totalDishes - totalPayable
        val discountPerPerson = discount / numPersons
        val discountRemainder = discount % numPersons

        val finalShares = MutableList(numPersons) { 0L }
        for (i in 0 until numPersons) {
            val deducted = if (i == 0) {
                personRawTotals[i] - (discountPerPerson + discountRemainder)
            } else {
                personRawTotals[i] - discountPerPerson
            }
            finalShares[i] = maxOf(0L, deducted)
        }

        val currentSum = finalShares.sum()
        val diff = totalPayable - currentSum
        if (diff != 0L) {
            finalShares[0] = finalShares[0] + diff
        }

        return Result.success(finalShares)
    }
}

class SplitBillRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    companion object {
        private const val TAG = "SplitBillRepository"
    }

    fun loadBookingAndOrderItems(
        bookingId: String,
        onResult: (Result<Pair<Booking, List<OrderItem>>>) -> Unit
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
                        onResult(Result.failure(IllegalStateException("PARSE_ERROR")))
                        return@addOnSuccessListener
                    }

                firestore.collection("orderItems")
                    .whereEqualTo("bookingId", bookingId)
                    .get()
                    .addOnSuccessListener { itemsSnap ->
                        val items = itemsSnap.documents.mapNotNull {
                            it.toObject(OrderItem::class.java)?.copy(id = it.id)
                        }
                        onResult(Result.success(Pair(booking, items)))
                    }
                    .addOnFailureListener { err ->
                        Log.e(TAG, "Lỗi đọc orderItems: ${err.message}", err)
                        onResult(Result.failure(err))
                    }
            }
            .addOnFailureListener { err ->
                Log.e(TAG, "Lỗi đọc booking: ${err.message}", err)
                onResult(Result.failure(err))
            }
    }

    fun getExistingGuests(
        bookingId: String,
        onResult: (Result<List<BookingGuest>>) -> Unit
    ) {
        if (bookingId.isBlank()) {
            onResult(Result.success(emptyList()))
            return
        }

        firestore.collection("bookingGuests")
            .whereEqualTo("bookingId", bookingId)
            .get()
            .addOnSuccessListener { snap ->
                val guests = snap.documents.mapNotNull {
                    it.toObject(BookingGuest::class.java)?.copy(id = it.id)
                }
                onResult(Result.success(guests))
            }
            .addOnFailureListener { err ->
                Log.e(TAG, "Lỗi đọc bookingGuests: ${err.message}", err)
                onResult(Result.failure(err))
            }
    }

    fun syncGuestsList(
        bookingId: String,
        guests: List<BookingGuest>,
        onResult: (Result<Unit>) -> Unit
    ) {
        if (bookingId.isBlank() || guests.isEmpty()) {
            onResult(Result.success(Unit))
            return
        }

        firestore.collection("bookingGuests")
            .whereEqualTo("bookingId", bookingId)
            .get()
            .addOnSuccessListener { snap ->
                val existingDocs = snap.documents
                val newGuestIds = guests.map { it.id }.toSet()

                val batch = firestore.batch()
                for (doc in existingDocs) {
                    if (!newGuestIds.contains(doc.id)) {
                        batch.delete(doc.reference)
                    }
                }

                for (g in guests) {
                    val docId = g.id.ifEmpty { "BG_${bookingId}_${g.userId.ifEmpty { System.nanoTime().toString() }}" }
                    val ref = firestore.collection("bookingGuests").document(docId)
                    batch.set(ref, g.copy(id = docId, bookingId = bookingId))
                }

                batch.commit()
                    .addOnSuccessListener {
                        onResult(Result.success(Unit))
                    }
                    .addOnFailureListener { err ->
                        Log.e(TAG, "Lỗi syncGuestsList: ${err.message}", err)
                        onResult(Result.failure(err))
                    }
            }
            .addOnFailureListener { err ->
                Log.e(TAG, "Lỗi đọc existing guests khi sync: ${err.message}", err)
                onResult(Result.failure(err))
            }
    }

    /**
     * Xác nhận thanh toán phần tiền (shareAmount) của một người:
     * - Chống double-tap: kiểm tra doc bookingGuests xem đã paid chưa
     * - Tạo 1 bản ghi Payment type = "split"
     * - Cập nhật paid = true
     * - Kiểm tra nếu toàn bộ người chia đã thanh toán xong -> đổi bookings.status = "PAID"
     * Trả về Boolean (true nếu tất cả đã thanh toán xong, false nếu còn người chưa thanh toán).
     */
    fun payShare(
        bookingId: String,
        guestId: String,
        userId: String,
        amount: Long,
        method: String,
        allGuestIds: List<String>,
        onResult: (Result<Boolean>) -> Unit
    ) {
        if (bookingId.isBlank() || guestId.isBlank()) {
            onResult(Result.failure(IllegalArgumentException("Dữ liệu thanh toán không hợp lệ")))
            return
        }

        val guestRef = firestore.collection("bookingGuests").document(guestId)
        val bookingRef = firestore.collection("bookings").document(bookingId)
        val paymentId = "PAY_SPLIT_${guestId}_${System.currentTimeMillis()}"
        val paymentRef = firestore.collection("payments").document(paymentId)

        firestore.runTransaction { tx ->
            // --- 1. TẤT CẢ CÁC ĐỌC (READS) PHẢI CHẠY TRƯỚC MỌI GHI (WRITES) ---
            val guestSnap = tx.get(guestRef)
            if (!guestSnap.exists()) {
                throw IllegalStateException("GUEST_NOT_FOUND")
            }

            val isAlreadyPaid = guestSnap.getBoolean("paid") ?: false
            if (isAlreadyPaid) {
                throw IllegalStateException("ALREADY_PAID")
            }

            val bookingSnap = tx.get(bookingRef)
            val paymentSnap = tx.get(paymentRef)

            val otherSnapshots = mutableMapOf<String, com.google.firebase.firestore.DocumentSnapshot>()
            for (otherId in allGuestIds) {
                if (otherId == guestId) continue
                otherSnapshots[otherId] = tx.get(firestore.collection("bookingGuests").document(otherId))
            }

            // --- 2. TẤT CẢ CÁC GHI (WRITES) ---
            val now = System.currentTimeMillis()
            val payment = Payment(
                id = paymentId,
                bookingId = bookingId,
                userId = userId,
                type = "split",
                amount = amount,
                method = method,
                status = "SUCCESS",
                paidAt = now
            )

            if (!paymentSnap.exists()) {
                tx.set(paymentRef, payment)
            }
            tx.update(guestRef, mapOf("paid" to true, "shareAmount" to amount))

            var allCompleted = true
            for (otherId in allGuestIds) {
                if (otherId == guestId) continue
                val otherSnap = otherSnapshots[otherId]
                val otherPaid = otherSnap?.getBoolean("paid") ?: false
                if (!otherPaid) {
                    allCompleted = false
                    break
                }
            }

            if (allCompleted && bookingSnap.exists()) {
                tx.update(bookingRef, "status", BookingStatus.PAID.name)
            }

            allCompleted
        }.addOnSuccessListener { allCompleted ->
            Log.i(TAG, "Đã thanh toán phần chia $guestId. Hoàn tất toàn bộ đơn: $allCompleted")
            onResult(Result.success(allCompleted))
        }.addOnFailureListener { err ->
            Log.e(TAG, "Lỗi thanh toán phần chia: ${err.message}", err)
            onResult(Result.failure(err))
        }
    }
}
