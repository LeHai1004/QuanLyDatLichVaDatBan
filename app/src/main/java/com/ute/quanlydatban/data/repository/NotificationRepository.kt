package com.ute.quanlydatban.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.ute.quanlydatban.data.model.AppNotification

class NotificationRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) : BaseRepository<AppNotification>(
    firestore.collection(NOTIFICATIONS_COLLECTION),
    AppNotification::class.java
) {

    val currentUserId: String?
        get() = auth.currentUser?.uid

    // Lấy danh sách thông báo của người dùng, sắp xếp mới nhất lên đầu
    fun getNotifications(
        userId: String,
        onResult: (Result<List<AppNotification>>) -> Unit
    ) {
        firestore.collection(NOTIFICATIONS_COLLECTION)
            .whereEqualTo("userId", userId)
            .get()
            .addOnSuccessListener { snapshot ->
                val list = snapshot.toObjects(AppNotification::class.java)
                if (list.isEmpty()) {
                    // Nếu chưa có thông báo nào, tự động tạo mẫu vài bản ghi cho tài khoản demo
                    seedDemoNotifications(userId) {
                        firestore.collection(NOTIFICATIONS_COLLECTION)
                            .whereEqualTo("userId", userId)
                            .get()
                            .addOnSuccessListener { snap2 ->
                                val seededList = snap2.toObjects(AppNotification::class.java)
                                onResult(Result.success(seededList.sortedByDescending { it.createdAt }))
                            }
                            .addOnFailureListener { onResult(Result.success(emptyList())) }
                    }
                } else {
                    onResult(Result.success(list.sortedByDescending { it.createdAt }))
                }
            }
            .addOnFailureListener { onResult(Result.failure(it)) }
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

    // Kiểm tra đơn đặt bàn có tồn tại hay không (tránh văng app khi đơn đã bị xóa)
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

    private fun seedDemoNotifications(userId: String, onDone: () -> Unit) {
        val now = System.currentTimeMillis()
        val demoItems = listOf(
            AppNotification(
                id = newId(),
                userId = userId,
                type = TYPE_BOOKING_NEW,
                title = "Đơn đặt bàn mới #BK101",
                body = "Khách hàng Nguyễn Văn An vừa đặt bàn 4 người vào 19:00 hôm nay.",
                read = false,
                createdAt = now - 5 * 60 * 1000L,
                targetId = "bk_demo_101"
            ),
            AppNotification(
                id = newId(),
                userId = userId,
                type = TYPE_GUEST_ARRIVED,
                title = "Khách đã đến quán - Bàn 2",
                body = "Khách hàng Trần Thị Mai đã check-in tại Bàn 2 (Tầng 1).",
                read = false,
                createdAt = now - 35 * 60 * 1000L,
                targetId = "bk_demo_102"
            ),
            AppNotification(
                id = newId(),
                userId = userId,
                type = TYPE_BOOKING_CANCELLED,
                title = "Khách hủy đặt bàn #BK098",
                body = "Khách hàng Lê Minh Tuấn đã hủy lịch hẹn lúc 18:30 do có việc bận.",
                read = true,
                createdAt = now - 2 * 3600 * 1000L,
                targetId = "bk_demo_deleted"
            )
        )

        val batch = firestore.batch()
        demoItems.forEach { item ->
            val ref = firestore.collection(NOTIFICATIONS_COLLECTION).document(item.id)
            batch.set(ref, item)
        }
        batch.commit()
            .addOnCompleteListener { onDone() }
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
