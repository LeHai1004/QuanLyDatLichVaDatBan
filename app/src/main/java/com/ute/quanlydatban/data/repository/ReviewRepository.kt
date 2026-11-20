package com.ute.quanlydatban.data.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.ute.quanlydatban.data.model.Booking
import com.ute.quanlydatban.data.model.BookingStatus
import com.ute.quanlydatban.data.model.Review

class ReviewRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) : BaseRepository<Review>(firestore.collection("reviews"), Review::class.java) {

    companion object {
        private const val TAG = "ReviewRepository"
    }

    /**
     * Gửi đánh giá cho đơn đặt bàn:
     * - Chỉ áp dụng cho đơn có trạng thái PAID
     * - Lưu bản ghi review mới vào collection reviews
     * - Cập nhật trạng thái đơn đặt bàn thành COMPLETED
     */
    fun createReview(
        review: Review,
        onResult: (Result<Unit>) -> Unit
    ) {
        if (review.bookingId.isBlank()) {
            onResult(Result.failure(IllegalArgumentException("bookingId không được để trống")))
            return
        }
        if (review.rating !in 1..5) {
            onResult(Result.failure(IllegalArgumentException("Số sao đánh giá phải từ 1 đến 5")))
            return
        }

        val reviewId = review.id.ifEmpty { "REV_${review.bookingId}" }
        val reviewRef = collection.document(reviewId)
        val bookingRef = firestore.collection("bookings").document(review.bookingId)

        firestore.runTransaction { transaction ->
            val bookingSnap = transaction.get(bookingRef)
            if (!bookingSnap.exists()) {
                throw IllegalStateException("Không tìm thấy đơn đặt bàn #${review.bookingId}")
            }

            val currentStatus = bookingSnap.getString("status") ?: ""
            if (currentStatus == BookingStatus.COMPLETED.name) {
                throw IllegalStateException("ALREADY_COMPLETED")
            }
            if (currentStatus != BookingStatus.PAID.name) {
                throw IllegalStateException("NOT_PAID")
            }

            val reviewSnap = transaction.get(reviewRef)
            if (reviewSnap.exists()) {
                throw IllegalStateException("ALREADY_REVIEWED")
            }

            val now = System.currentTimeMillis()
            val finalReview = review.copy(
                id = reviewId,
                createdAt = if (review.createdAt > 0) review.createdAt else now
            )

            transaction.set(reviewRef, finalReview)
            transaction.update(bookingRef, "status", BookingStatus.COMPLETED.name)
            null
        }.addOnSuccessListener {
            Log.i(TAG, "Đã lưu đánh giá cho đơn #${review.bookingId}")
            onResult(Result.success(Unit))
        }.addOnFailureListener { error ->
            Log.e(TAG, "Lỗi tạo đánh giá: ${error.message}", error)
            onResult(Result.failure(error))
        }
    }

    fun getReviewForBooking(
        bookingId: String,
        onResult: (Result<Review?>) -> Unit
    ) {
        if (bookingId.isBlank()) {
            onResult(Result.success(null))
            return
        }
        val reviewId = "REV_$bookingId"
        collection.document(reviewId).get()
            .addOnSuccessListener { snap ->
                if (snap.exists()) {
                    onResult(Result.success(snap.toObject(Review::class.java)))
                } else {
                    // Thử tìm theo trường bookingId nếu doc id không theo chuẩn REV_
                    collection.whereEqualTo("bookingId", bookingId).limit(1).get()
                        .addOnSuccessListener { querySnap ->
                            val r = querySnap.documents.firstOrNull()?.toObject(Review::class.java)
                            onResult(Result.success(r))
                        }
                        .addOnFailureListener { error ->
                            onResult(Result.failure(error))
                        }
                }
            }
            .addOnFailureListener { error ->
                onResult(Result.failure(error))
            }
    }
}
