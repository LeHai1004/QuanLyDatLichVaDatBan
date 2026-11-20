package com.ute.quanlydatban.data.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.ute.quanlydatban.data.model.Booking
import com.ute.quanlydatban.data.model.BookingStatus
import com.ute.quanlydatban.data.model.DiningTable

class TableRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    companion object {
        private const val TAG = "TableRepository"
    }

    private fun tablesCollection(restaurantId: String) =
        firestore.collection("restaurants").document(restaurantId).collection("tables")

    fun listenTables(
        restaurantId: String,
        onChange: (Result<List<DiningTable>>) -> Unit
    ): ListenerRegistration {
        return tablesCollection(restaurantId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "Lỗi listenTables: ${error.message}", error)
                onChange(Result.failure(error))
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val tables = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(DiningTable::class.java)?.copy(id = doc.id)
                }
                onChange(Result.success(tables))
            } else {
                onChange(Result.success(emptyList()))
            }
        }
    }

    fun getTables(
        restaurantId: String,
        onResult: (Result<List<DiningTable>>) -> Unit
    ) {
        if (restaurantId.isBlank()) {
            onResult(Result.success(emptyList()))
            return
        }
        tablesCollection(restaurantId).get()
            .addOnSuccessListener { snapshot ->
                val tables = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(DiningTable::class.java)?.copy(id = doc.id)
                }
                onResult(Result.success(tables))
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "Lỗi getTables: ${error.message}", error)
                onResult(Result.failure(error))
            }
    }

    fun getTable(
        restaurantId: String,
        tableId: String,
        onResult: (Result<DiningTable?>) -> Unit
    ) {
        if (restaurantId.isBlank() || tableId.isBlank()) {
            onResult(Result.success(null))
            return
        }
        tablesCollection(restaurantId).document(tableId).get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    val table = snapshot.toObject(DiningTable::class.java)?.copy(id = snapshot.id)
                    onResult(Result.success(table))
                } else {
                    onResult(Result.success(null))
                }
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "Lỗi getTable: ${error.message}", error)
                onResult(Result.failure(error))
            }
    }

    fun saveTable(
        restaurantId: String,
        table: DiningTable,
        onResult: (Result<Unit>) -> Unit
    ) {
        if (restaurantId.isBlank()) {
            onResult(Result.failure(IllegalArgumentException("restaurantId không được để trống")))
            return
        }
        val cleanName = table.name.trim()
        if (cleanName.isBlank()) {
            onResult(Result.failure(IllegalArgumentException("Tên bàn không được để trống")))
            return
        }
        if (table.capacity <= 0) {
            onResult(Result.failure(IllegalArgumentException("Sức chứa phải lớn hơn 0")))
            return
        }

        val targetCollection = tablesCollection(restaurantId)

        // Kiểm tra tên trùng trong cùng nhà hàng
        targetCollection.whereEqualTo("name", cleanName).get()
            .addOnSuccessListener { querySnap ->
                val isDuplicate = querySnap.documents.any { doc ->
                    doc.id != table.id
                }
                if (isDuplicate) {
                    onResult(Result.failure(IllegalStateException("DUPLICATE_NAME")))
                    return@addOnSuccessListener
                }

                val tableId = table.id.ifEmpty { targetCollection.document().id }
                val qrCode = table.qrCode.ifEmpty { "TABLE_${cleanName.replace(" ", "_").uppercase()}_$tableId" }
                val finalTable = table.copy(
                    id = tableId,
                    name = cleanName,
                    qrCode = qrCode
                )

                targetCollection.document(tableId).set(finalTable)
                    .addOnSuccessListener {
                        Log.i(TAG, "Đã lưu bàn $tableId thành công")
                        onResult(Result.success(Unit))
                    }
                    .addOnFailureListener { error ->
                        Log.e(TAG, "Lỗi lưu bàn: ${error.message}", error)
                        onResult(Result.failure(error))
                    }
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "Lỗi kiểm tra tên bàn trùng: ${error.message}", error)
                onResult(Result.failure(error))
            }
    }

    fun deleteTable(
        restaurantId: String,
        tableId: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        if (restaurantId.isBlank() || tableId.isBlank()) {
            onResult(Result.failure(IllegalArgumentException("Thiếu restaurantId hoặc tableId")))
            return
        }

        // Kiểm tra xem bàn có đang có đơn hiệu lực không
        firestore.collection("bookings")
            .whereEqualTo("restaurantId", restaurantId)
            .whereEqualTo("tableId", tableId)
            .get()
            .addOnSuccessListener { snapshot ->
                val hasActiveBooking = snapshot.documents.any { doc ->
                    val b = doc.toObject(Booking::class.java) ?: return@any false
                    val st = BookingStatus.from(b.status)
                    st == BookingStatus.CONFIRMED ||
                        st == BookingStatus.CHECKED_IN ||
                        st == BookingStatus.SERVING ||
                        st == BookingStatus.WAITING
                }

                if (hasActiveBooking) {
                    onResult(Result.failure(IllegalStateException("TABLE_HAS_ACTIVE_BOOKING")))
                    return@addOnSuccessListener
                }

                tablesCollection(restaurantId).document(tableId).delete()
                    .addOnSuccessListener {
                        Log.i(TAG, "Đã xóa bàn $tableId")
                        onResult(Result.success(Unit))
                    }
                    .addOnFailureListener { error ->
                        Log.e(TAG, "Lỗi xóa bàn: ${error.message}", error)
                        onResult(Result.failure(error))
                    }
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "Lỗi kiểm tra booking khi xóa bàn: ${error.message}", error)
                onResult(Result.failure(error))
            }
    }

    fun listenTodayBookings(
        restaurantId: String,
        todayDateStr: String,
        onChange: (Result<List<Booking>>) -> Unit
    ): ListenerRegistration {
        return firestore.collection("bookings")
            .whereEqualTo("restaurantId", restaurantId)
            .whereEqualTo("date", todayDateStr)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Lỗi listenTodayBookings: ${error.message}", error)
                    onChange(Result.failure(error))
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val bookings = snapshot.toObjects(Booking::class.java)
                    onChange(Result.success(bookings))
                } else {
                    onChange(Result.success(emptyList()))
                }
            }
    }
}
