package com.ute.quanlydatban.data.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.ute.quanlydatban.data.model.MenuItem
import com.ute.quanlydatban.data.model.OrderItem
import com.ute.quanlydatban.data.model.OrderItemStatus
import com.ute.quanlydatban.util.SeedData

class OrderRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) : BaseRepository<OrderItem>(firestore.collection("orderItems"), OrderItem::class.java) {

    companion object {
        private const val TAG = "OrderRepository"
        const val SOURCE_PRE = "pre"
        const val SOURCE_TABLE = "table"
    }

    // Tải danh sách thực đơn của quán
    fun getMenuItems(restaurantId: String, onResult: (Result<List<MenuItem>>) -> Unit) {
        if (restaurantId.isBlank()) {
            Log.e(TAG, "getMenuItems: restaurantId rỗng")
            onResult(Result.success(emptyList()))
            return
        }

        firestore.collection("menuItems")
            .whereEqualTo("restaurantId", restaurantId)
            .get()
            .addOnSuccessListener { snapshot ->
                val items = snapshot.toObjects(MenuItem::class.java)
                if (items.isEmpty()) {
                    // Nếu chưa có, nạp seed data mẫu
                    SeedData.load(firestore) { success ->
                        if (success) {
                            firestore.collection("menuItems")
                                .whereEqualTo("restaurantId", restaurantId)
                                .get()
                                .addOnSuccessListener { retrySnap ->
                                    onResult(Result.success(retrySnap.toObjects(MenuItem::class.java)))
                                }
                                .addOnFailureListener { error ->
                                    Log.e(TAG, "Lỗi getMenuItems sau seed: ${error.message}", error)
                                    onResult(Result.failure(error))
                                }
                        } else {
                            onResult(Result.success(emptyList()))
                        }
                    }
                } else {
                    onResult(Result.success(items))
                }
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "Lỗi getMenuItems($restaurantId): ${error.message}", error)
                onResult(Result.failure(error))
            }
    }

    // Lưu các món đặt trước vào collection orderItems trên Firestore
    fun savePreOrderItems(
        bookingId: String,
        items: List<OrderItem>,
        onResult: (Result<Unit>) -> Unit
    ) {
        if (bookingId.isBlank()) {
            val err = IllegalArgumentException("bookingId không được để trống")
            Log.e(TAG, err.message ?: "")
            onResult(Result.failure(err))
            return
        }

        if (items.isEmpty()) {
            onResult(Result.success(Unit))
            return
        }

        val batch = firestore.batch()
        for (item in items) {
            val id = item.id.ifEmpty { firestore.collection("orderItems").document().id }
            val itemRef = firestore.collection("orderItems").document(id)
            val finalItem = item.copy(
                id = id,
                bookingId = bookingId,
                source = SOURCE_PRE,
                status = OrderItemStatus.WAITING.name
            )
            batch.set(itemRef, finalItem)
        }

        batch.commit()
            .addOnSuccessListener {
                onResult(Result.success(Unit))
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "Lỗi savePreOrderItems cho booking #$bookingId: ${error.message}", error)
                onResult(Result.failure(error))
            }
    }

    // Lấy các món đã gọi của đơn
    fun getOrderItems(bookingId: String, onResult: (Result<List<OrderItem>>) -> Unit) {
        if (bookingId.isBlank()) {
            Log.e(TAG, "getOrderItems: bookingId rỗng")
            onResult(Result.success(emptyList()))
            return
        }

        firestore.collection("orderItems")
            .whereEqualTo("bookingId", bookingId)
            .get()
            .addOnSuccessListener { snapshot ->
                onResult(Result.success(snapshot.toObjects(OrderItem::class.java)))
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "Lỗi getOrderItems: ${error.message}", error)
                onResult(Result.failure(error))
            }
    }

    // Đổi trạng thái món qua máy trạng thái OrderItemStatus
    fun updateOrderItemStatus(
        orderItemId: String,
        nextStatus: OrderItemStatus,
        onResult: (Result<Unit>) -> Unit
    ) {
        if (orderItemId.isBlank()) {
            val err = IllegalArgumentException("orderItemId không được để trống")
            Log.e(TAG, err.message ?: "")
            onResult(Result.failure(err))
            return
        }

        collection.document(orderItemId).get()
            .addOnSuccessListener { snapshot ->
                val item = snapshot.toObject(OrderItem::class.java)
                if (item == null) {
                    val err = IllegalStateException("Không tìm thấy món #$orderItemId")
                    Log.e(TAG, err.message ?: "")
                    onResult(Result.failure(err))
                    return@addOnSuccessListener
                }

                val currentStatus = OrderItemStatus.from(item.status)
                if (!currentStatus.canTransitionTo(nextStatus)) {
                    val err = IllegalStateException("Không thể chuyển trạng thái món từ $currentStatus sang $nextStatus")
                    Log.e(TAG, err.message ?: "")
                    onResult(Result.failure(err))
                    return@addOnSuccessListener
                }

                collection.document(orderItemId).update("status", nextStatus.name)
                    .addOnSuccessListener { onResult(Result.success(Unit)) }
                    .addOnFailureListener { error ->
                        Log.e(TAG, "Lỗi updateOrderItemStatus: ${error.message}", error)
                        onResult(Result.failure(error))
                    }
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "Lỗi đọc orderItem: ${error.message}", error)
                onResult(Result.failure(error))
            }
    }

    // Lưu các món gọi tại bàn vào collection orderItems trên Firestore với source = table, status = waiting
    fun saveTableOrderItems(
        bookingId: String,
        items: List<OrderItem>,
        onResult: (Result<Unit>) -> Unit
    ) {
        if (bookingId.isBlank()) {
            val err = IllegalArgumentException("bookingId không được để trống")
            Log.e(TAG, err.message ?: "")
            onResult(Result.failure(err))
            return
        }

        if (items.isEmpty()) {
            onResult(Result.success(Unit))
            return
        }

        val batch = firestore.batch()
        for (item in items) {
            val id = item.id.ifEmpty { firestore.collection("orderItems").document().id }
            val itemRef = firestore.collection("orderItems").document(id)
            val finalItem = item.copy(
                id = id,
                bookingId = bookingId,
                source = SOURCE_TABLE,
                status = OrderItemStatus.WAITING.name
            )
            batch.set(itemRef, finalItem)
        }

        batch.commit()
            .addOnSuccessListener {
                onResult(Result.success(Unit))
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "Lỗi saveTableOrderItems cho booking #$bookingId: ${error.message}", error)
                onResult(Result.failure(error))
            }
    }

    /**
     * Lắng nghe danh sách các món đã gọi của đơn hàng theo bookingId realtime
     */
    fun listenOrderItemsForBooking(
        bookingId: String,
        onChange: (Result<List<OrderItem>>) -> Unit
    ): com.google.firebase.firestore.ListenerRegistration {
        if (bookingId.isBlank()) {
            onChange(Result.success(emptyList()))
            return collection.whereEqualTo("bookingId", "__NONE__").addSnapshotListener { _, _ -> }
        }

        return collection.whereEqualTo("bookingId", bookingId)
            .addSnapshotListener { snap, error ->
                when {
                    error != null -> {
                        Log.e(TAG, "Lỗi listenOrderItemsForBooking($bookingId): ${error.message}", error)
                        onChange(Result.failure(error))
                    }
                    snap != null -> {
                        val list = snap.documents.mapNotNull { doc ->
                            try {
                                val obj = doc.toObject(OrderItem::class.java)
                                obj?.copy(id = doc.id)
                            } catch (_: Exception) {
                                null
                            }
                        }
                        onChange(Result.success(list))
                    }
                }
            }
    }

    /**
     * Lắng nghe danh sách tất cả các món cần làm của bếp cho nhà hàng theo realtime
     */
    fun listenKitchenOrdersForRestaurant(
        restaurantId: String,
        onChange: (Result<List<OrderItem>>) -> Unit
    ): com.google.firebase.firestore.ListenerRegistration {
        if (restaurantId.isBlank()) {
            onChange(Result.success(emptyList()))
            return collection.whereEqualTo("bookingId", "__NONE__").addSnapshotListener { _, _ -> }
        }

        return collection.addSnapshotListener { snap, error ->
            when {
                error != null -> {
                    Log.e(TAG, "Lỗi listenKitchenOrdersForRestaurant($restaurantId): ${error.message}", error)
                    onChange(Result.failure(error))
                }
                snap != null -> {
                    val list = snap.documents.mapNotNull { doc ->
                        try {
                            val obj = doc.toObject(OrderItem::class.java)
                            obj?.copy(id = doc.id)
                        } catch (_: Exception) {
                            null
                        }
                    }.filter { item ->
                        val status = OrderItemStatus.from(item.status)
                        status != OrderItemStatus.SERVED
                    }
                    onChange(Result.success(list))
                }
            }
        }
    }

    /**
     * Đổi trạng thái món qua Firestore Transaction để tránh race condition (2 bếp bấm cùng lúc)
     */
    fun updateOrderItemStatusTransaction(
        orderItemId: String,
        expectedCurrentStatus: OrderItemStatus,
        nextStatus: OrderItemStatus,
        onResult: (Result<Unit>) -> Unit
    ) {
        if (orderItemId.isBlank()) {
            onResult(Result.failure(IllegalArgumentException("orderItemId không được để trống")))
            return
        }

        val itemRef = collection.document(orderItemId)

        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(itemRef)
            if (!snapshot.exists()) {
                throw IllegalStateException("Không tìm thấy món #$orderItemId")
            }

            val currentStatusStr = snapshot.getString("status") ?: OrderItemStatus.WAITING.name
            val currentStatus = OrderItemStatus.from(currentStatusStr)

            if (currentStatus != expectedCurrentStatus) {
                throw IllegalStateException("STATUS_CONFLICT")
            }

            if (!currentStatus.canTransitionTo(nextStatus)) {
                throw IllegalStateException("STATUS_CONFLICT")
            }

            transaction.update(itemRef, "status", nextStatus.name)
            null
        }.addOnSuccessListener {
            onResult(Result.success(Unit))
        }.addOnFailureListener { error ->
            Log.e(TAG, "Lỗi updateOrderItemStatusTransaction: ${error.message}", error)
            onResult(Result.failure(error))
        }
    }
}
