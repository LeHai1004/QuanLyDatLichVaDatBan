package com.ute.quanlydatban.data.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.ute.quanlydatban.data.model.Booking
import com.ute.quanlydatban.data.model.BookingStatus
import com.ute.quanlydatban.data.model.ServiceRequest
import com.ute.quanlydatban.data.model.ServiceRequestStatus

class ServiceRequestRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) : BaseRepository<ServiceRequest>(firestore.collection("serviceRequests"), ServiceRequest::class.java) {

    companion object {
        private const val TAG = "ServiceRequestRepo"
    }

    /**
     * Gửi yêu cầu phục vụ từ khách hàng
     * Document ID cố định dạng: "{bookingId}_{type}" để tự động chống trùng yêu cầu
     */
    fun sendServiceRequest(
        booking: Booking,
        type: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        if (booking.id.isBlank() || booking.restaurantId.isBlank()) {
            onResult(Result.failure(IllegalArgumentException("Thiếu thông tin đặt bàn")))
            return
        }

        val status = BookingStatus.from(booking.status)
        if (status == BookingStatus.PAID || status == BookingStatus.COMPLETED) {
            onResult(Result.failure(IllegalStateException("BOOKING_PAID")))
            return
        }

        val requestId = "${booking.id}_${type}"
        val reqRef = collection.document(requestId)

        val billReqId = "${booking.id}_BILL"
        val billReqRef = collection.document(billReqId)

        firestore.runTransaction { tx ->
            // 1. Kiểm tra xem đã có yêu cầu BILL cho booking này chưa (bất kể pending hay handled)
            val billSnap = tx.get(billReqRef)
            if (billSnap.exists()) {
                val billStatus = billSnap.getString("status").orEmpty()
                if (billStatus == ServiceRequestStatus.PENDING || billStatus == ServiceRequestStatus.HANDLED) {
                    throw IllegalStateException("BILL_REQUESTED")
                }
            }

            // 2. Kiểm tra yêu cầu trùng cùng loại đang chờ xử lý
            val snap = tx.get(reqRef)
            if (snap.exists()) {
                val currentStatus = snap.getString("status") ?: ServiceRequestStatus.PENDING
                if (currentStatus == ServiceRequestStatus.PENDING) {
                    throw IllegalStateException("PENDING_EXISTS")
                }
            }

            val newReq = ServiceRequest(
                id = requestId,
                bookingId = booking.id,
                tableId = booking.tableId,
                restaurantId = booking.restaurantId,
                type = type,
                status = ServiceRequestStatus.PENDING,
                createdAt = System.currentTimeMillis()
            )

            tx.set(reqRef, newReq)
            null
        }.addOnSuccessListener {
            onResult(Result.success(Unit))
        }.addOnFailureListener { err ->
            Log.e(TAG, "Lỗi sendServiceRequest: ${err.message}", err)
            onResult(Result.failure(err))
        }
    }

    /**
     * Lắng nghe danh sách các yêu cầu của booking này (khách hàng)
     */
    fun listenCustomerRequests(
        bookingId: String,
        onChange: (Result<List<ServiceRequest>>) -> Unit
    ): ListenerRegistration {
        if (bookingId.isBlank()) {
            onChange(Result.success(emptyList()))
            return collection.whereEqualTo("bookingId", "__NONE__").addSnapshotListener { _, _ -> }
        }

        return collection.whereEqualTo("bookingId", bookingId)
            .addSnapshotListener { snap, error ->
                when {
                    error != null -> onChange(Result.failure(error))
                    snap != null -> {
                        val list = snap.documents.mapNotNull { doc ->
                            try {
                                val obj = doc.toObject(ServiceRequest::class.java)
                                obj?.copy(id = doc.id)
                            } catch (_: Exception) {
                                null
                            }
                        }.sortedByDescending { it.createdAt }
                        onChange(Result.success(list))
                    }
                }
            }
    }

    /**
     * Lắng nghe tất cả yêu cầu phục vụ của quán (chủ quán)
     */
    fun listenOwnerRequests(
        restaurantId: String,
        onChange: (Result<List<ServiceRequest>>) -> Unit
    ): ListenerRegistration {
        if (restaurantId.isBlank()) {
            onChange(Result.success(emptyList()))
            return collection.whereEqualTo("restaurantId", "__NONE__").addSnapshotListener { _, _ -> }
        }

        return collection.whereEqualTo("restaurantId", restaurantId)
            .addSnapshotListener { snap, error ->
                when {
                    error != null -> onChange(Result.failure(error))
                    snap != null -> {
                        val list = snap.documents.mapNotNull { doc ->
                            try {
                                val obj = doc.toObject(ServiceRequest::class.java)
                                obj?.copy(id = doc.id)
                            } catch (_: Exception) {
                                null
                            }
                        }.sortedByDescending { it.createdAt }
                        onChange(Result.success(list))
                    }
                }
            }
    }

    /**
     * Đánh dấu yêu cầu đã xử lý
     */
    fun markHandled(
        requestId: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        if (requestId.isBlank()) {
            onResult(Result.failure(IllegalArgumentException("requestId rỗng")))
            return
        }

        collection.document(requestId).update(
            mapOf(
                "status" to ServiceRequestStatus.HANDLED,
                "handledAt" to System.currentTimeMillis()
            )
        ).addOnSuccessListener {
            onResult(Result.success(Unit))
        }.addOnFailureListener { err ->
            Log.e(TAG, "Lỗi markHandled: ${err.message}", err)
            onResult(Result.failure(err))
        }
    }
}
