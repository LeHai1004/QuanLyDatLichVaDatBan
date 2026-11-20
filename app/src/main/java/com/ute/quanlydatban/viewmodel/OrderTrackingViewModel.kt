package com.ute.quanlydatban.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.firebase.firestore.ListenerRegistration
import com.ute.quanlydatban.data.model.OrderItem
import com.ute.quanlydatban.data.model.OrderItemStatus
import com.ute.quanlydatban.data.repository.OrderRepository

class OrderTrackingViewModel(
    private val orderRepository: OrderRepository = OrderRepository()
) : ViewModel() {

    private val _orderItems = MutableLiveData<List<OrderItem>>(emptyList())
    val orderItems: LiveData<List<OrderItem>> = _orderItems

    private val _isServiceCompleted = MutableLiveData<Boolean>(false)
    val isServiceCompleted: LiveData<Boolean> = _isServiceCompleted

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private var listenerRegistration: ListenerRegistration? = null

    fun startListening(bookingId: String) {
        if (bookingId.isBlank()) return
        _isLoading.value = true
        _errorMessage.value = null

        stopListening()
        listenerRegistration = orderRepository.listenOrderItemsForBooking(bookingId) { result ->
            _isLoading.value = false
            result.onSuccess { items ->
                _orderItems.value = items
                val allServed = items.isNotEmpty() && items.all {
                    OrderItemStatus.from(it.status) == OrderItemStatus.SERVED
                }
                _isServiceCompleted.value = allServed
            }.onFailure { err ->
                Log.e("OrderTrackingVM", "Lỗi lắng nghe orderItems: ${err.message}", err)
                _errorMessage.value = "Không thể tải danh sách món ăn. Vui lòng thử lại."
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
        private val repo: OrderRepository = OrderRepository()
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return OrderTrackingViewModel(repo) as T
        }
    }
}
