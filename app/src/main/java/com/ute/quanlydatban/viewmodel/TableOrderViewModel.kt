package com.ute.quanlydatban.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.ute.quanlydatban.data.model.MenuItem
import com.ute.quanlydatban.data.model.OrderItem
import com.ute.quanlydatban.data.model.OrderItemStatus
import com.ute.quanlydatban.data.repository.OrderRepository

class TableOrderViewModel(
    private val orderRepository: OrderRepository = OrderRepository()
) : ViewModel() {

    private val _menuItems = MutableLiveData<List<MenuItem>>(emptyList())
    val menuItems: LiveData<List<MenuItem>> = _menuItems

    private val _quantities = MutableLiveData<MutableMap<String, Long>>(mutableMapOf())
    val quantities: LiveData<MutableMap<String, Long>> = _quantities

    private val _totalPrice = MutableLiveData<Long>(0L)
    val totalPrice: LiveData<Long> = _totalPrice

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private val _orderSuccess = MutableLiveData<Boolean>(false)
    val orderSuccess: LiveData<Boolean> = _orderSuccess

    fun loadMenu(restaurantId: String) {
        if (restaurantId.isBlank()) return
        _isLoading.value = true
        _errorMessage.value = null

        orderRepository.getMenuItems(restaurantId) { result ->
            _isLoading.value = false
            result.onSuccess { items ->
                _menuItems.value = items
            }.onFailure { err ->
                Log.e("TableOrderVM", "Lỗi tải menu: ${err.message}", err)
                _errorMessage.value = "Không thể tải thực đơn nhà hàng."
            }
        }
    }

    fun updateQuantity(menuItemId: String, delta: Long) {
        val currentMap = _quantities.value ?: mutableMapOf()
        val currentQty = currentMap[menuItemId] ?: 0L
        val newQty = (currentQty + delta).coerceAtLeast(0L)
        if (newQty > 0L) {
            currentMap[menuItemId] = newQty
        } else {
            currentMap.remove(menuItemId)
        }
        _quantities.value = currentMap
        calculateTotal()
    }

    private fun calculateTotal() {
        val menu = _menuItems.value.orEmpty()
        val map = _quantities.value.orEmpty()
        var sum = 0L
        for ((id, qty) in map) {
            val item = menu.find { it.id == id }
            if (item != null) {
                sum += item.price * qty
            }
        }
        _totalPrice.value = sum
    }

    fun submitOrder(bookingId: String) {
        val map = _quantities.value.orEmpty()
        if (map.isEmpty()) {
            _errorMessage.value = "Vui lòng chọn ít nhất 1 món ăn."
            return
        }

        val menu = _menuItems.value.orEmpty()
        val orderItems = mutableListOf<OrderItem>()
        for ((id, qty) in map) {
            val m = menu.find { it.id == id }
            if (m != null && m.available && qty > 0L) {
                orderItems.add(
                    OrderItem(
                        bookingId = bookingId,
                        menuItemId = m.id,
                        name = m.name,
                        qty = qty,
                        unitPrice = m.price,
                        source = OrderRepository.SOURCE_TABLE,
                        status = OrderItemStatus.WAITING.name
                    )
                )
            }
        }

        if (orderItems.isEmpty()) {
            _errorMessage.value = "Vui lòng chọn ít nhất 1 món ăn hợp lệ."
            return
        }

        _isLoading.value = true
        _errorMessage.value = null

        orderRepository.saveTableOrderItems(bookingId, orderItems) { result ->
            _isLoading.value = false
            result.onSuccess {
                _orderSuccess.value = true
            }.onFailure { err ->
                Log.e("TableOrderVM", "Lỗi gửi order: ${err.message}", err)
                _errorMessage.value = "Gửi order thất bại. Vui lòng thử lại."
            }
        }
    }

    fun clearMessages() {
        _errorMessage.value = null
        _orderSuccess.value = false
    }

    class Factory(
        private val repo: OrderRepository = OrderRepository()
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return TableOrderViewModel(repo) as T
        }
    }
}
