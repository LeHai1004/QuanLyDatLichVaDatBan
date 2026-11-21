package com.ute.quanlydatban.data.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.ute.quanlydatban.data.model.AppNotification
import com.ute.quanlydatban.data.model.Booking
import com.ute.quanlydatban.data.model.BookingStatus

class NotificationRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) : BaseRepository<AppNotification>(
    firestore.collection(NOTIFICATIONS_COLLECTION),
    AppNotification::class.java
) {

    val currentUserId: String?
        get() = auth.currentUser?.uid

    /**
     * Tạo thông báo từ các sự kiện hệ thống (WAITING, CONFIRMED, REJECTED, EXPIRED, CANCELLED, CHECKED_IN)
     * Document ID: "{bookingId}_{eventType}" dùng set() để ghi đè (không trùng lặp).
     */
    fun createEventNotification(
        booking: Booking,
        status: BookingStatus,
        onResult: ((Result<Unit>) -> Unit)? = null
    ) {
        if (booking.id.isBlank()) {
            Log.w("NotiEvent", "createEventNotification: booking.id rỗng")
            onResult?.invoke(Result.success(Unit))
            return
        }

        val eventTypeStr = status.name
        val notiId = "${booking.id}_$eventTypeStr"
        val notiRef = firestore.collection(NOTIFICATIONS_COLLECTION).document(notiId)

        val isForOwner = when (status) {
            BookingStatus.WAITING,
            BookingStatus.CANCELLED,
            BookingStatus.CHECKED_IN -> true
            BookingStatus.CONFIRMED,
            BookingStatus.REJECTED,
            BookingStatus.EXPIRED -> false
            else -> {
                onResult?.invoke(Result.success(Unit))
                return
            }
        }

        fun sendNoti(recipientUserId: String, notiType: String, title: String, body: String) {
            val targetUserId = recipientUserId.ifEmpty {
                auth.currentUser?.uid.orEmpty().ifEmpty { "user_default" }
            }

            Log.e("NotiEvent", "Starting sendNoti -> notiId: $notiId, eventType: $eventTypeStr, targetUserId: $targetUserId")

            val noti = AppNotification(
                id = notiId,
                userId = targetUserId,
                type = notiType,
                title = title,
                body = body,
                read = false,
                createdAt = System.currentTimeMillis(),
                targetId = booking.id
            )

            notiRef.set(noti)
                .addOnSuccessListener {
                    Log.e("NotiEvent", "THÀNH CÔNG: Đã tạo thông báo doc $notiId cho userId $targetUserId (event: $eventTypeStr)")
                    onResult?.invoke(Result.success(Unit))
                }
                .addOnFailureListener { err ->
                    Log.e("NotiEvent", "THẤT BẠI: Lỗi ghi Firestore doc $notiId: ${err.message}", err)
                    onResult?.invoke(Result.failure(err))
                }
        }

        if (isForOwner) {
            if (booking.restaurantId.isBlank()) {
                Log.w("NotiEvent", "Thiếu restaurantId cho booking #${booking.id}, gửi cho current user hoặc 'owner'")
                val fallbackOwner = auth.currentUser?.uid.orEmpty().ifEmpty { "owner" }
                val (type, title, body) = when (status) {
                    BookingStatus.WAITING -> Triple(
                        TYPE_BOOKING_NEW,
                        "Đơn đặt bàn mới #${booking.id}",
                        "Đơn đặt bàn #${booking.id} đã hoàn tất đặt cọc, chờ bạn xác nhận."
                    )
                    BookingStatus.CANCELLED -> Triple(
                        TYPE_BOOKING_CANCELLED,
                        "Đơn đặt bàn #${booking.id} đã bị hủy",
                        "Khách hàng đã hủy đơn đặt bàn #${booking.id}."
                    )
                    BookingStatus.CHECKED_IN -> Triple(
                        TYPE_GUEST_ARRIVED,
                        "Khách đã đến quán - Đơn #${booking.id}",
                        "Khách hàng của đơn #${booking.id} đã đến nhận bàn."
                    )
                    else -> Triple(TYPE_GENERAL, "Thông báo đơn #${booking.id}", "")
                }
                sendNoti(fallbackOwner, type, title, body)
                return
            }

            firestore.collection("restaurants").document(booking.restaurantId).get()
                .addOnSuccessListener { rSnap ->
                    var ownerId = rSnap.getString("ownerId").orEmpty()
                    if (ownerId.isBlank()) {
                        Log.w("NotiEvent", "Thiếu ownerId trong doc restaurants/${booking.restaurantId}, dùng current user làm fallback")
                        ownerId = auth.currentUser?.uid.orEmpty().ifEmpty { "owner" }
                    }
                    val (type, title, body) = when (status) {
                        BookingStatus.WAITING -> Triple(
                            TYPE_BOOKING_NEW,
                            "Đơn đặt bàn mới #${booking.id}",
                            "Đơn đặt bàn #${booking.id} đã hoàn tất đặt cọc, chờ bạn xác nhận."
                        )
                        BookingStatus.CANCELLED -> Triple(
                            TYPE_BOOKING_CANCELLED,
                            "Đơn đặt bàn #${booking.id} đã bị hủy",
                            "Khách hàng đã hủy đơn đặt bàn #${booking.id}."
                        )
                        BookingStatus.CHECKED_IN -> Triple(
                            TYPE_GUEST_ARRIVED,
                            "Khách đã đến quán - Đơn #${booking.id}",
                            "Khách hàng của đơn #${booking.id} đã đến nhận bàn."
                        )
                        else -> Triple(TYPE_GENERAL, "Thông báo đơn #${booking.id}", "")
                    }
                    sendNoti(ownerId, type, title, body)
                }
                .addOnFailureListener { err ->
                    Log.e("NotiEvent", "Lỗi đọc restaurant ${booking.restaurantId}: ${err.message}, gửi fallback owner", err)
                    val fallbackOwner = auth.currentUser?.uid.orEmpty().ifEmpty { "owner" }
                    sendNoti(fallbackOwner, TYPE_BOOKING_NEW, "Đơn đặt bàn mới #${booking.id}", "Có đơn đặt bàn mới #${booking.id}.")
                }
        } else {
            val customerUserId = booking.userId.ifEmpty {
                auth.currentUser?.uid.orEmpty().ifEmpty { "customer" }
            }
            val (type, title, body) = when (status) {
                BookingStatus.CONFIRMED -> Triple(
                    "booking_confirmed",
                    "Đơn đặt bàn #${booking.id} đã được xác nhận",
                    "Nhà hàng đã xác nhận đơn đặt bàn #${booking.id} của bạn."
                )
                BookingStatus.REJECTED -> Triple(
                    "booking_rejected",
                    "Đơn đặt bàn #${booking.id} đã bị từ chối",
                    "Đơn đặt bàn #${booking.id} đã bị từ chối và tiền cọc sẽ được hoàn lại."
                )
                BookingStatus.EXPIRED -> Triple(
                    TYPE_BOOKING_CANCELLED,
                    "Đơn đặt bàn #${booking.id} đã hết hạn",
                    "Đơn đặt bàn #${booking.id} đã hết hạn do quá thời gian giữ chỗ."
                )
                else -> Triple(TYPE_GENERAL, "Thông báo đơn #${booking.id}", "")
            }
            sendNoti(customerUserId, type, title, body)
        }
    }

    /**
     * Lấy danh sách thông báo của người dùng theo userId
     * Sắp xếp giảm dần theo createdAt ở client.
     */
    fun getNotifications(
        userId: String,
        onResult: (Result<List<AppNotification>>) -> Unit
    ) {
        Log.e("NotiDebug", "getNotifications - current uid: $userId")
        if (userId.isBlank()) {
            onResult(Result.success(emptyList()))
            return
        }

        firestore.collection(NOTIFICATIONS_COLLECTION)
            .whereEqualTo("userId", userId)
            .get()
            .addOnSuccessListener { snapshot ->
                Log.e("NotiDebug", "Firestore returned ${snapshot.size()} docs for userId: $userId")
                val list = snapshot.documents.mapNotNull { doc ->
                    try {
                        val obj = doc.toObject(AppNotification::class.java)
                        val idVal = if (obj?.id.isNullOrBlank()) doc.id else obj.id
                        val userIdVal = doc.getString("userId").orEmpty().ifEmpty { obj?.userId.orEmpty() }
                        val titleVal = doc.getString("title").orEmpty().ifEmpty { obj?.title.orEmpty() }
                        val bodyVal = doc.getString("body").orEmpty().ifEmpty { obj?.body.orEmpty() }
                        val typeVal = doc.getString("type").orEmpty().ifEmpty { obj?.type.orEmpty() }
                        val targetIdVal = doc.getString("targetId").orEmpty().ifEmpty { obj?.targetId.orEmpty() }
                        val readVal = doc.getBoolean("read") ?: obj?.read ?: false
                        val createdAtVal = doc.getLong("createdAt") ?: obj?.createdAt ?: 0L

                        AppNotification(
                            id = idVal,
                            userId = userIdVal,
                            type = typeVal,
                            title = titleVal,
                            body = bodyVal,
                            read = readVal,
                            createdAt = createdAtVal,
                            targetId = targetIdVal
                        )
                    } catch (e: Exception) {
                        Log.e("NotiDebug", "Error parsing doc ${doc.id}: ${e.message}", e)
                        null
                    }
                }.sortedByDescending { it.createdAt }

                Log.e("NotiDebug", "Parsed notifications count: ${list.size}")
                onResult(Result.success(list))
            }
            .addOnFailureListener { err ->
                Log.e("NotiDebug", "Lỗi query notifications: ${err.message}", err)
                onResult(Result.failure(err))
            }
    }

    fun observeNotifications(
        userId: String,
        onResult: (List<AppNotification>) -> Unit,
        onError: (Exception) -> Unit
    ): com.google.firebase.firestore.ListenerRegistration {
        if (userId.isBlank()) {
            onResult(emptyList())
            return firestore.collection(NOTIFICATIONS_COLLECTION).whereEqualTo("userId", "__NONE__").addSnapshotListener { _, _ -> }
        }

        return firestore.collection(NOTIFICATIONS_COLLECTION)
            .whereEqualTo("userId", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("NotiRepo", "observeNotifications error: ${error.message}", error)
                    onError(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { doc ->
                        try {
                            val obj = doc.toObject(AppNotification::class.java)
                            val idVal = if (obj?.id.isNullOrBlank()) doc.id else obj.id
                            val userIdVal = doc.getString("userId").orEmpty().ifEmpty { obj?.userId.orEmpty() }
                            val titleVal = doc.getString("title").orEmpty().ifEmpty { obj?.title.orEmpty() }
                            val bodyVal = doc.getString("body").orEmpty().ifEmpty { obj?.body.orEmpty() }
                            val typeVal = doc.getString("type").orEmpty().ifEmpty { obj?.type.orEmpty() }
                            val targetIdVal = doc.getString("targetId").orEmpty().ifEmpty { obj?.targetId.orEmpty() }
                            val readVal = doc.getBoolean("read") ?: obj?.read ?: false
                            val createdAtVal = doc.getLong("createdAt") ?: obj?.createdAt ?: 0L

                            AppNotification(
                                id = idVal,
                                userId = userIdVal,
                                type = typeVal,
                                title = titleVal,
                                body = bodyVal,
                                read = readVal,
                                createdAt = createdAtVal,
                                targetId = targetIdVal
                            )
                        } catch (e: Exception) {
                            null
                        }
                    }.sortedByDescending { it.createdAt }
                    onResult(list)
                }
            }
    }

    // Đánh dấu một thông báo là đã đọc
    fun markAsRead(
        notificationId: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        update(notificationId, mapOf("read" to true), onResult)
    }

    // Đánh dấu tất cả thông báo của user là đã đọc
    fun markAllAsRead(
        userId: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        firestore.collection(NOTIFICATIONS_COLLECTION)
            .whereEqualTo("userId", userId)
            .whereEqualTo("read", false)
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) {
                    onResult(Result.success(Unit))
                    return@addOnSuccessListener
                }
                val batch = firestore.batch()
                snapshot.documents.forEach { doc ->
                    batch.update(doc.reference, "read", true)
                }
                batch.commit()
                    .addOnSuccessListener { onResult(Result.success(Unit)) }
                    .addOnFailureListener { onResult(Result.failure(it)) }
            }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    // Kiểm tra đơn đặt bàn có tồn tại hay không
    fun checkBookingExists(
        bookingId: String,
        onResult: (Result<Boolean>) -> Unit
    ) {
        if (bookingId.isBlank()) {
            onResult(Result.success(false))
            return
        }
        firestore.collection(BOOKINGS_COLLECTION).document(bookingId).get()
            .addOnSuccessListener { snapshot ->
                onResult(Result.success(snapshot.exists()))
            }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    companion object {
        const val NOTIFICATIONS_COLLECTION = "notifications"
        const val BOOKINGS_COLLECTION = "bookings"
        const val TYPE_BOOKING_NEW = "booking_new"
        const val TYPE_BOOKING_CANCELLED = "booking_cancelled"
        const val TYPE_GUEST_ARRIVED = "guest_arrived"
        const val TYPE_GENERAL = "general"
        const val ROLE_CUSTOMER = "customer"
        const val ROLE_OWNER = "owner"
    }
}
