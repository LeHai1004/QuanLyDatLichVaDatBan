package com.ute.quanlydatban.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.ute.quanlydatban.data.model.Booking
import com.ute.quanlydatban.data.model.OrderItem
import com.ute.quanlydatban.data.model.OrderItemStatus
import com.ute.quanlydatban.data.repository.BookingRepository
import com.ute.quanlydatban.data.repository.OrderRepository

data class KitchenItemDisplay(
    val orderItem: OrderItem,
    val tableName: String = "",
    val sourceText: String = ""
)

class OwnerKitchenViewModel(
    private val orderRepository: OrderRepository = OrderRepository(),
    private val bookingRepository: BookingRepository = BookingRepository()
) : ViewModel() {

    private val _kitchenItems = MutableLiveData<List<KitchenItemDisplay>>(emptyList())
    val kitchenItems: LiveData<List<KitchenItemDisplay>> = _kitchenItems

    private val _isOffline = MutableLiveData<Boolean>(false)
    val isOffline: LiveData<Boolean> = _isOffline

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private var listenerRegistration: ListenerRegistration? = null

    fun startListening(restaurantId: String) {
        if (restaurantId.isBlank()) return
        _isLoading.value = true
        _errorMessage.value = null

        stopListening()
        listenerRegistration = orderRepository.listenKitchenOrdersForRestaurant(restaurantId) { result ->
            _isLoading.value = false
            result.onSuccess { rawItems ->
                enrichKitchenItems(restaurantId, rawItems)
            }.onFailure { err ->
                Log.e("OwnerKitchenVM", "Lỗi lắng nghe kitchen items: ${err.message}", err)
                _errorMessage.value = "Không thể tải danh sách món ăn bếp."
            }
        }
    }

    private fun enrichKitchenItems(restaurantId: String, items: List<OrderItem>) {
        if (items.isEmpty()) {
            _kitchenItems.value = emptyList()
            return
        }

        val firestore = FirebaseFirestore.getInstance()
        val bookingIds = items.map { it.bookingId }.distinct()

        firestore.collection("bookings").get().addOnSuccessListener { bSnap ->
            val bookingMap = bSnap.documents.mapNotNull { doc ->
                try {
                    val b = doc.toObject(Booking::class.java)?.copy(id = doc.id)
                    if (b != null && b.restaurantId == restaurantId) b else null
                } catch (e: Exception) {
                    null
                }
            }.associateBy { it.id }

            val displayList = items.mapNotNull { item ->
                val booking = bookingMap[item.bookingId]
                if (booking == null) null
                else {
                    val srcText = if (item.source == OrderRepository.SOURCE_TABLE) "Tại bàn" else "Đặt trước"
                    val tName = booking.tableId.ifEmpty { "Bàn chung" }
                    KitchenItemDisplay(
                        orderItem = item,
                        tableName = tName,
                        sourceText = srcText
                    )
                }
            }

            _kitchenItems.value = displayList
            fetchTableNames(restaurantId, displayList)
        }.addOnFailureListener {
            val fallbackList = items.map { item ->
                val srcText = if (item.source == OrderRepository.SOURCE_TABLE) "Tại bàn" else "Đặt trước"
                KitchenItemDisplay(item, "Bàn #${item.bookingId.takeLast(4)}", srcText)
            }
            _kitchenItems.value = fallbackList
        }
    }

    private fun fetchTableNames(restaurantId: String, currentList: List<KitchenItemDisplay>) {
        val firestore = FirebaseFirestore.getInstance()
        firestore.collection("restaurants").document(restaurantId)
            .collection("tables").get()
            .addOnSuccessListener { tSnap ->
                val tableMap = tSnap.documents.associate { doc ->
                    doc.id to (doc.getString("name") ?: doc.id)
                }
                val updated = currentList.map { item ->
                    val resolvedName = tableMap[item.tableName] ?: item.tableName
                    item.copy(tableName = resolvedName)
                }
                _kitchenItems.value = updated
            }
    }

    fun transitionStatus(
        item: KitchenItemDisplay,
        nextStatus: OrderItemStatus,
        onResult: (Boolean, String?) -> Unit
    ) {
        val currentStatus = OrderItemStatus.from(item.orderItem.status)
        orderRepository.updateOrderItemStatusTransaction(
            orderItemId = item.orderItem.id,
            expectedCurrentStatus = currentStatus,
            nextStatus = nextStatus
        ) { result ->
            result.onSuccess {
                onResult(true, null)
            }.onFailure { err ->
                val msg = if (err.message?.contains("STATUS_CONFLICT") == true) {
                    "Món đã được cập nhật bởi người khác"
                } else {
                    err.message ?: "Không thể đổi trạng thái món"
                }
                onResult(false, msg)
            }
        }
    }

    fun stopListening() {
        listenerRegistration?.remove()
        listenerRegistration = null
    }

    fun clearError() {
        _errorMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        stopListening()
    }

    class Factory(
        private val orderRepo: OrderRepository = OrderRepository(),
        private val bookingRepo: BookingRepository = BookingRepository()
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return OwnerKitchenViewModel(orderRepo, bookingRepo) as T
        }
    }
}
