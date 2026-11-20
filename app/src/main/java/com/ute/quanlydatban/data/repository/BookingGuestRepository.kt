package com.ute.quanlydatban.data.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.ute.quanlydatban.data.model.Booking
import com.ute.quanlydatban.data.model.BookingGuest
import com.ute.quanlydatban.data.model.BookingStatus
import com.ute.quanlydatban.data.model.MenuItem
import com.ute.quanlydatban.data.model.OrderItem
import com.ute.quanlydatban.data.model.OrderItemStatus

object InviteFriendsValidator {
    val CLOSED_STATUSES = setOf(
        BookingStatus.EXPIRED.name,
        BookingStatus.CANCELLED.name,
        BookingStatus.PAID.name,
        BookingStatus.CHECKED_IN.name,
        BookingStatus.SERVING.name,
        BookingStatus.REJECTED.name,
        BookingStatus.NO_SHOW.name
    )

    fun validateJoinEligibility(
        booking: Booking?,
        existingGuests: List<BookingGuest>,
        currentUserId: String
    ): Result<Unit> {
        if (booking == null || booking.id.isBlank()) {
            return Result.failure(IllegalArgumentException("Mã mời không tồn tại hoặc không hợp lệ."))
        }
        if (currentUserId.isBlank()) {
            return Result.failure(IllegalStateException("NOT_LOGGED_IN"))
        }
        if (booking.userId.isNotBlank() && booking.userId == currentUserId) {
            return Result.failure(IllegalStateException("SELF_INVITE"))
        }
        if (CLOSED_STATUSES.contains(booking.status)) {
            return Result.failure(IllegalStateException("NOT_OPEN"))
        }

        val alreadyAccepted = existingGuests.any { it.userId == currentUserId && it.status == "accepted" }
        if (alreadyAccepted) {
            return Result.failure(IllegalStateException("ALREADY_JOINED"))
        }

        val acceptedCount = existingGuests.count { it.status == "accepted" } + 1
        val maxGuests = if (booking.guests > 0) booking.guests else 2L
        if (acceptedCount >= maxGuests) {
            return Result.failure(IllegalStateException("FULL"))
        }

        return Result.success(Unit)
    }
}

class BookingGuestRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    companion object {
        private const val TAG = "BookingGuestRepository"
    }

    fun listenGuestsForBooking(
        bookingId: String,
        onResult: (Result<List<BookingGuest>>) -> Unit
    ): ListenerRegistration {
        if (bookingId.isBlank()) {
            onResult(Result.success(emptyList()))
            return ListenerRegistration { }
        }

        return firestore.collection("bookingGuests")
            .whereEqualTo("bookingId", bookingId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Lỗi lắng nghe bookingGuests: ${error.message}", error)
                    onResult(Result.failure(error))
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(BookingGuest::class.java)?.copy(id = doc.id)
                    }
                    onResult(Result.success(list))
                } else {
                    onResult(Result.success(emptyList()))
                }
            }
    }

    fun listenOrderItemsForBooking(
        bookingId: String,
        onResult: (Result<List<OrderItem>>) -> Unit
    ): ListenerRegistration {
        if (bookingId.isBlank()) {
            onResult(Result.success(emptyList()))
            return ListenerRegistration { }
        }

        return firestore.collection("orderItems")
            .whereEqualTo("bookingId", bookingId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Lỗi lắng nghe orderItems: ${error.message}", error)
                    onResult(Result.failure(error))
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(OrderItem::class.java)?.copy(id = doc.id)
                    }
                    onResult(Result.success(list))
                } else {
                    onResult(Result.success(emptyList()))
                }
            }
    }

    fun getBooking(
        bookingId: String,
        onResult: (Result<Booking?>) -> Unit
    ) {
        if (bookingId.isBlank()) {
            onResult(Result.success(null))
            return
        }

        firestore.collection("bookings").document(bookingId).get()
            .addOnSuccessListener { snap ->
                if (snap.exists()) {
                    val b = snap.toObject(Booking::class.java)?.copy(id = snap.id)
                    onResult(Result.success(b))
                } else {
                    onResult(Result.success(null))
                }
            }
            .addOnFailureListener { err ->
                Log.e(TAG, "Lỗi đọc booking #$bookingId: ${err.message}", err)
                onResult(Result.failure(err))
            }
    }

    fun findBookingByCode(
        rawCode: String,
        onResult: (Result<Booking?>) -> Unit
    ) {
        val clean = rawCode.trim()
            .removePrefix("INVITE-")
            .removePrefix("BT-")
            .removePrefix("BT")
            .removePrefix("#BT-")
            .trim()

        if (clean.isBlank()) {
            onResult(Result.success(null))
            return
        }

        // Tìm trực tiếp theo document ID
        firestore.collection("bookings").document(clean).get()
            .addOnSuccessListener { snap ->
                if (snap.exists()) {
                    onResult(Result.success(snap.toObject(Booking::class.java)?.copy(id = snap.id)))
                    return@addOnSuccessListener
                }

                // Tìm theo trường qrCode
                firestore.collection("bookings")
                    .whereEqualTo("qrCode", rawCode.trim())
                    .limit(1)
                    .get()
                    .addOnSuccessListener { qrSnap ->
                        if (!qrSnap.isEmpty) {
                            val doc = qrSnap.documents.first()
                            onResult(Result.success(doc.toObject(Booking::class.java)?.copy(id = doc.id)))
                        } else {
                            onResult(Result.success(null))
                        }
                    }
                    .addOnFailureListener { err ->
                        onResult(Result.failure(err))
                    }
            }
            .addOnFailureListener { err ->
                onResult(Result.failure(err))
            }
    }

    fun acceptInvitation(
        booking: Booking,
        userId: String,
        userName: String,
        userAvatarUrl: String = "",
        onResult: (Result<BookingGuest>) -> Unit
    ) {
        val guestsRef = firestore.collection("bookingGuests")

        guestsRef.whereEqualTo("bookingId", booking.id).get()
            .addOnSuccessListener { snap ->
                val currentGuests = snap.documents.mapNotNull { it.toObject(BookingGuest::class.java) }
                val validation = InviteFriendsValidator.validateJoinEligibility(booking, currentGuests, userId)

                if (validation.isFailure) {
                    onResult(Result.failure(validation.exceptionOrNull()!!))
                    return@addOnSuccessListener
                }

                val docId = "BG_${booking.id}_$userId"
                val guest = BookingGuest(
                    id = docId,
                    bookingId = booking.id,
                    userId = userId,
                    userName = userName.ifBlank { "Bạn bè" },
                    avatarUrl = userAvatarUrl,
                    status = "accepted",
                    shareAmount = 0L,
                    paid = false
                )

                guestsRef.document(docId).set(guest)
                    .addOnSuccessListener {
                        Log.i(TAG, "Đã chấp nhận lời mời cho user $userId vào đơn ${booking.id}")
                        onResult(Result.success(guest))
                    }
                    .addOnFailureListener { err ->
                        Log.e(TAG, "Lỗi ghi bookingGuest: ${err.message}", err)
                        onResult(Result.failure(err))
                    }
            }
            .addOnFailureListener { err ->
                Log.e(TAG, "Lỗi đọc danh sách khách: ${err.message}", err)
                onResult(Result.failure(err))
            }
    }

    fun declineInvitation(
        bookingId: String,
        userId: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        if (bookingId.isBlank() || userId.isBlank()) {
            onResult(Result.failure(IllegalArgumentException("Thông tin không hợp lệ")))
            return
        }

        val docId = "BG_${bookingId}_$userId"
        val guestRef = firestore.collection("bookingGuests").document(docId)

        guestRef.update("status", "declined")
            .addOnSuccessListener {
                onResult(Result.success(Unit))
            }
            .addOnFailureListener {
                // Nếu chưa có document thì tạo với status declined
                val guest = BookingGuest(
                    id = docId,
                    bookingId = bookingId,
                    userId = userId,
                    status = "declined"
                )
                guestRef.set(guest)
                    .addOnSuccessListener { onResult(Result.success(Unit)) }
                    .addOnFailureListener { err -> onResult(Result.failure(err)) }
            }
    }

    fun addGuestOrderItem(
        bookingId: String,
        menuItem: MenuItem,
        qty: Long,
        userId: String,
        userName: String,
        onResult: (Result<OrderItem>) -> Unit
    ) {
        val orderItemId = firestore.collection("orderItems").document().id
        val orderItem = OrderItem(
            id = orderItemId,
            bookingId = bookingId,
            menuItemId = menuItem.id,
            name = menuItem.name,
            qty = qty,
            unitPrice = menuItem.price,
            source = "pre",
            status = OrderItemStatus.WAITING.name,
            userId = userId,
            userName = userName
        )

        firestore.collection("orderItems").document(orderItemId).set(orderItem)
            .addOnSuccessListener {
                Log.i(TAG, "Đã thêm món $orderItemId cho khách $userName")
                onResult(Result.success(orderItem))
            }
            .addOnFailureListener { err ->
                Log.e(TAG, "Lỗi thêm món: ${err.message}", err)
                onResult(Result.failure(err))
            }
    }
}
