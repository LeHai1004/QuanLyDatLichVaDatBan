package com.ute.quanlydatban.data.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.ute.quanlydatban.data.model.AppNotification
import com.ute.quanlydatban.data.model.Booking
import com.ute.quanlydatban.data.model.BookingStatus
import com.ute.quanlydatban.data.model.DiningTable
import com.ute.quanlydatban.data.model.WaitlistEntry

class WaitlistRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {

    companion object {
        private const val TAG = "WaitlistRepository"
        const val WAITLIST_COLLECTION = "waitlist"
        const val NOTIFICATIONS_COLLECTION = "notifications"
        const val TYPE_WAITLIST_AVAILABLE = "WAITLIST_AVAILABLE"
        const val STATUS_CANCELLED = "cancelled"
    }

    val currentUserId: String?
        get() = auth.currentUser?.uid

    private val waitlistCollection
        get() = firestore.collection(WAITLIST_COLLECTION)

    /**
     * Kiểm tra xem còn bàn trống phù hợp với số người tại ngày giờ yêu cầu hay không.
     */
    fun checkTableAvailable(
        restaurantId: String,
        date: String,
        time: String,
        guests: Long,
        onResult: (Result<Boolean>) -> Unit
    ) {
        if (restaurantId.isBlank()) {
            onResult(Result.success(false))
            return
        }

        firestore.collection("restaurants").document(restaurantId).collection("tables").get()
            .addOnSuccessListener { tableSnapshot ->
                val allTables = tableSnapshot.documents.mapNotNull { doc ->
                    doc.toObject(DiningTable::class.java)?.copy(id = doc.id)
                }

                // Nếu không có bàn nào đủ sức chứa khách -> không có bàn trống phù hợp
                val candidateTables = allTables.filter { it.capacity >= guests }
                if (candidateTables.isEmpty()) {
                    onResult(Result.success(false))
                    return@addOnSuccessListener
                }

                // Kiểm tra các booking đã đặt cho nhà hàng này
                firestore.collection("bookings")
                    .whereEqualTo("restaurantId", restaurantId)
                    .get()
                    .addOnSuccessListener { bookingSnapshot ->
                        val activeBookings = bookingSnapshot.documents.mapNotNull { doc ->
                            doc.toObject(Booking::class.java)
                        }.filter { booking ->
                            val status = BookingStatus.from(booking.status)
                            booking.date.trim() == date.trim() &&
                                    booking.time.trim() == time.trim() &&
                                    status != BookingStatus.CANCELLED &&
                                    status != BookingStatus.EXPIRED &&
                                    status != BookingStatus.REJECTED
                        }

                        val bookedTableIds = activeBookings.map { it.tableId }.toSet()
                        val availableTable = WaitlistValidator.findSuitableTable(candidateTables, bookedTableIds, guests)

                        // Nếu tìm thấy bàn còn trống -> true, ngược lại false
                        onResult(Result.success(availableTable != null))
                    }
                    .addOnFailureListener { error ->
                        Log.e(TAG, "Lỗi kiểm tra booking: ${error.message}", error)
                        onResult(Result.failure(error))
                    }
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "Lỗi đọc danh sách bàn: ${error.message}", error)
                onResult(Result.failure(error))
            }
    }

    /**
     * Đăng ký vào danh sách chờ.
     */
    fun registerWaitlist(
        entry: WaitlistEntry,
        onResult: (Result<String>) -> Unit
    ) {
        val now = System.currentTimeMillis()
        if (WaitlistValidator.isPastTime(entry.date, entry.time, now)) {
            onResult(Result.failure(IllegalArgumentException("Không thể đăng ký chờ cho thời gian trong quá khứ")))
            return
        }

        // Kiểm tra xem đã có lượt chờ trùng chưa
        waitlistCollection
            .whereEqualTo("userId", entry.userId)
            .get()
            .addOnSuccessListener { snapshot ->
                val entries = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(WaitlistEntry::class.java)?.copy(id = doc.id)
                }

                val hasDuplicate = WaitlistValidator.hasDuplicateWaiting(
                    entries, entry.userId, entry.restaurantId, entry.date, entry.time
                )

                if (hasDuplicate) {
                    onResult(Result.failure(IllegalStateException("Bạn đã đăng ký chờ khung giờ này rồi")))
                    return@addOnSuccessListener
                }

                val docRef = waitlistCollection.document()
                val entryToSave = entry.copy(
                    id = docRef.id,
                    createdAt = now,
                    status = "waiting",
                    handled = false
                )

                docRef.set(entryToSave)
                    .addOnSuccessListener {
                        onResult(Result.success(docRef.id))
                    }
                    .addOnFailureListener { error ->
                        Log.e(TAG, "Lỗi lưu waitlist: ${error.message}", error)
                        onResult(Result.failure(error))
                    }
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "Lỗi kiểm tra trùng waitlist: ${error.message}", error)
                onResult(Result.failure(error))
            }
    }

    /**
     * Lắng nghe danh sách chờ của khách hàng hiện tại (kèm tự động đánh dấu hết hạn).
     */
    fun listenMyWaitlist(
        userId: String,
        onChange: (Result<List<WaitlistEntry>>) -> Unit
    ): ListenerRegistration {
        return waitlistCollection
            .whereEqualTo("userId", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Lỗi listenMyWaitlist: ${error.message}", error)
                    onChange(Result.failure(error))
                    return@addSnapshotListener
                }

                val entries = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(WaitlistEntry::class.java)?.copy(id = doc.id)
                } ?: emptyList()

                // Tự động kiểm tra hết hạn
                val now = System.currentTimeMillis()
                entries.forEach { entry ->
                    if (entry.status == "waiting" && WaitlistValidator.isExpired(entry.date, entry.time, now)) {
                        waitlistCollection.document(entry.id).update("status", "expired")
                    }
                }

                onChange(Result.success(entries.sortedByDescending { it.createdAt }))
            }
    }

    /**
     * Lắng nghe danh sách chờ cho Chủ quán.
     */
    fun listenOwnerWaitlist(
        restaurantId: String,
        date: String? = null,
        onChange: (Result<List<WaitlistEntry>>) -> Unit
    ): ListenerRegistration {
        return waitlistCollection
            .whereEqualTo("restaurantId", restaurantId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Lỗi listenOwnerWaitlist: ${error.message}", error)
                    onChange(Result.failure(error))
                    return@addSnapshotListener
                }

                val allEntries = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(WaitlistEntry::class.java)?.copy(id = doc.id)
                } ?: emptyList()

                // Tự động đánh dấu hết hạn cho các bản ghi đang chờ mà quá giờ
                val now = System.currentTimeMillis()
                allEntries.forEach { entry ->
                    if (entry.status == "waiting" && WaitlistValidator.isExpired(entry.date, entry.time, now)) {
                        waitlistCollection.document(entry.id).update("status", "expired")
                    }
                }

                val entries = if (!date.isNullOrBlank()) {
                    allEntries.filter { it.date.trim() == date.trim() }
                } else {
                    allEntries
                }

                onChange(Result.success(entries.sortedBy { it.createdAt }))
            }
    }

    /**
     * Chủ quán đánh dấu đã xử lý yêu cầu chờ.
     */
    fun markHandled(
        waitlistId: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        if (waitlistId.isBlank()) {
            onResult(Result.failure(IllegalArgumentException("waitlistId không được để trống")))
            return
        }

        waitlistCollection.document(waitlistId)
            .update("handled", true)
            .addOnSuccessListener {
                onResult(Result.success(Unit))
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "Lỗi markHandled: ${error.message}", error)
                onResult(Result.failure(error))
            }
    }

    /**
     * Kiểm tra và thông báo cho khách hàng chờ tiếp theo (FIFO) khi có bàn trống.
     */
    fun notifyNextInWaitlist(
        restaurantId: String,
        date: String,
        time: String,
        freedTableCapacity: Long,
        onResult: (Result<WaitlistEntry?>) -> Unit
    ) {
        waitlistCollection
            .whereEqualTo("restaurantId", restaurantId)
            .get()
            .addOnSuccessListener { snapshot ->
                val entries = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(WaitlistEntry::class.java)?.copy(id = doc.id)
                }.filter { entry ->
                    entry.date.trim() == date.trim() &&
                            entry.time.trim() == time.trim() &&
                            entry.status == "waiting"
                }

                val candidate = WaitlistValidator.findNextCandidate(entries, freedTableCapacity)
                if (candidate == null) {
                    onResult(Result.success(null))
                    return@addOnSuccessListener
                }

                // Cập nhật trạng thái waitlist sang "notified"
                waitlistCollection.document(candidate.id)
                    .update("status", "notified")
                    .addOnSuccessListener {
                        // Tạo thông báo vào collection notifications
                        val notiId = "waitlist_${candidate.id}_${System.currentTimeMillis()}"
                        val noti = AppNotification(
                            id = notiId,
                            userId = candidate.userId,
                            type = TYPE_WAITLIST_AVAILABLE,
                            title = "Đã có bàn trống tại ${candidate.restaurantName.ifEmpty { "quán" }}",
                            body = "Quán ${candidate.restaurantName.ifEmpty { "nhà hàng" }} đã có bàn trống cho ${candidate.guests} người vào lúc ${candidate.time} ngày ${candidate.date}. Mời bạn vào đặt bàn ngay!",
                            read = false,
                            createdAt = System.currentTimeMillis(),
                            targetId = candidate.restaurantId
                        )

                        firestore.collection(NOTIFICATIONS_COLLECTION).document(notiId).set(noti)
                            .addOnSuccessListener {
                                Log.i(TAG, "Đã gửi thông báo bàn trống cho khách ${candidate.userId}")
                                onResult(Result.success(candidate.copy(status = "notified")))
                            }
                            .addOnFailureListener { notiErr ->
                                Log.e(TAG, "Lỗi tạo notification: ${notiErr.message}", notiErr)
                                onResult(Result.success(candidate.copy(status = "notified")))
                            }
                    }
                    .addOnFailureListener { error ->
                        Log.e(TAG, "Lỗi cập nhật notified cho waitlist: ${error.message}", error)
                        onResult(Result.failure(error))
                    }
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "Lỗi truy vấn waitlist để notify: ${error.message}", error)
                onResult(Result.failure(error))
            }
    }

    /**
     * Hủy lượt đăng ký chờ của khách hàng
     */
    fun cancelWaitlist(
        waitlistId: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        if (waitlistId.isBlank()) {
            onResult(Result.failure(IllegalArgumentException("waitlistId rỗng")))
            return
        }

        val uid = currentUserId.orEmpty()
        val docRef = waitlistCollection.document(waitlistId)

        firestore.runTransaction { tx ->
            val snap = tx.get(docRef)
            if (!snap.exists()) {
                throw IllegalStateException("Không tìm thấy lượt đăng ký chờ")
            }

            val entry = snap.toObject(WaitlistEntry::class.java)
                ?: throw IllegalStateException("Không đọc được dữ liệu")

            if (uid.isNotBlank() && entry.userId != uid) {
                throw IllegalStateException("UNAUTHORIZED")
            }

            if (entry.status == "expired" || entry.status == STATUS_CANCELLED) {
                throw IllegalStateException("ALREADY_CLOSED")
            }

            tx.update(docRef, "status", STATUS_CANCELLED)
            null
        }.addOnSuccessListener {
            onResult(Result.success(Unit))
        }.addOnFailureListener { err ->
            Log.e(TAG, "Lỗi cancelWaitlist: ${err.message}", err)
            onResult(Result.failure(err))
        }
    }
}
