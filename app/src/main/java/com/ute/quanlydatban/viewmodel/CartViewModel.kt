package com.ute.quanlydatban.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.ute.quanlydatban.data.model.MenuItem
import com.ute.quanlydatban.data.model.OrderItem
import com.ute.quanlydatban.data.model.OrderItemStatus
import com.ute.quanlydatban.data.model.Restaurant
import com.ute.quanlydatban.data.repository.BookingRepository
import com.ute.quanlydatban.data.repository.OrderRepository

data class CartItemUiModel(
    val menuItem: MenuItem,
    val quantity: Long,
    val unitPrice: Long = menuItem.price
) {
    val totalPrice: Long get() = quantity * unitPrice
}

class CartViewModel(
    private val orderRepository: OrderRepository = OrderRepository(),
    private val bookingRepository: BookingRepository = BookingRepository()
) : ViewModel() {

    var bookingId: String = ""
        private set
    var restaurantId: String = ""
        private set
    var tableName: String = ""
        private set
    var bookingDate: String = ""
        private set
    var bookingTime: String = ""
        private set
    var bookingGuests: Long = 2L
        private set

    private val _restaurant = MutableLiveData<Restaurant?>()
    val restaurant: LiveData<Restaurant?> = _restaurant

    private val _menuItems = MutableLiveData<List<MenuItem>>(emptyList())
    val menuItems: LiveData<List<MenuItem>> = _menuItems

    private val _selectedCategory = MutableLiveData<String>("Tất cả")
    val selectedCategory: LiveData<String> = _selectedCategory

    private val _cartItems = MutableLiveData<Map<String, CartItemUiModel>>(emptyMap())
    val cartItems: LiveData<Map<String, CartItemUiModel>> = _cartItems

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    fun initData(
        bId: String,
        rId: String,
        rName: String? = null,
        tName: String? = null,
        date: String? = null,
        time: String? = null,
        guests: Long? = null,
        deposit: Long? = null
    ) {
        bookingId = bId
        restaurantId = rId
        tableName = tName.orEmpty()
        bookingDate = date.orEmpty()
        bookingTime = time.orEmpty()
        bookingGuests = guests ?: 2L

        if (rName != null && _restaurant.value == null) {
            _restaurant.value = Restaurant(
                id = rId,
                name = rName,
                depositAmount = deposit ?: 100_000L
            )
        }

        if (rId.isNotBlank()) {
            bookingRepository.getRestaurant(rId) { resResult ->
                resResult.onSuccess { res ->
                    if (res != null) {
                        _restaurant.value = res
                    }
                }
            }
            loadMenu(rId)
        }
    }

    fun loadMenu(rId: String) {
        if (rId.isBlank()) return
        _isLoading.value = true
        _errorMessage.value = null

        orderRepository.getMenuItems(rId) { result ->
            _isLoading.value = false
            result.onSuccess { list ->
                _menuItems.value = list
            }.onFailure { error ->
                Log.e("CartViewModel", "Lỗi loadMenu: ${error.message}", error)
                _errorMessage.value = "Không thể tải thực đơn. Vui lòng thử lại."
            }
        }
    }

    fun setCategory(category: String) {
        _selectedCategory.value = category
    }

    // Thêm món vào giỏ
    fun addItem(item: MenuItem): Boolean {
        if (!item.available) {
            _errorMessage.value = "Món này hiện đang tạm hết."
            return false
        }

        val current = _cartItems.value?.toMutableMap() ?: mutableMapOf()
        val existing = current[item.id]
        if (existing != null) {
            current[item.id] = existing.copy(quantity = existing.quantity + 1)
        } else {
            // Chụp giá unitPrice tại thời điểm gọi để sau này giá menu đổi không làm sai đơn
            current[item.id] = CartItemUiModel(
                menuItem = item,
                quantity = 1,
                unitPrice = item.price
            )
        }
        _cartItems.value = current
        return true
    }

    // Giảm số lượng món
    fun decreaseItem(menuItemId: String) {
        val current = _cartItems.value?.toMutableMap() ?: return
        val existing = current[menuItemId] ?: return
        if (existing.quantity > 1) {
            current[menuItemId] = existing.copy(quantity = existing.quantity - 1)
        } else {
            current.remove(menuItemId)
        }
        _cartItems.value = current
    }

    // Xóa hoàn toàn món khỏi giỏ
    fun removeItem(menuItemId: String) {
        val current = _cartItems.value?.toMutableMap() ?: return
        current.remove(menuItemId)
        _cartItems.value = current
    }

    // Tổng số lượng món
    fun getTotalQuantity(): Long {
        return _cartItems.value?.values?.sumOf { it.quantity } ?: 0L
    }

    // Tổng tiền tạm tính của giỏ
    fun getSubtotal(): Long {
        return _cartItems.value?.values?.sumOf { it.totalPrice } ?: 0L
    }

    // Tiền đặt cọc của quán
    fun getDeposit(): Long {
        return _restaurant.value?.depositAmount ?: 100_000L
    }

    // Lưu món ăn đặt trước lên Firestore
    fun submitPreOrder(onComplete: (Boolean) -> Unit) {
        val currentCart = _cartItems.value.orEmpty()
        // Giỏ trống: vẫn cho tiếp tục sang bước cọc/thanh toán (không bắt buộc chọn món)
        if (currentCart.isEmpty()) {
            onComplete(true)
            return
        }

        if (bookingId.isBlank()) {
            _errorMessage.value = "Không tìm thấy mã đơn đặt bàn."
            onComplete(false)
            return
        }

        _isLoading.value = true
        _errorMessage.value = null

        val orderItemsToSave = currentCart.values.map { cartItem ->
            OrderItem(
                bookingId = bookingId,
                menuItemId = cartItem.menuItem.id,
                name = cartItem.menuItem.name,
                qty = cartItem.quantity,
                unitPrice = cartItem.unitPrice,
                source = OrderRepository.SOURCE_PRE,
                status = OrderItemStatus.WAITING.name
            )
        }

        orderRepository.savePreOrderItems(bookingId, orderItemsToSave) { result ->
            _isLoading.value = false
            result.onSuccess {
                onComplete(true)
            }.onFailure { error ->
                Log.e("CartViewModel", "Lỗi lưu pre-order: ${error.message}", error)
                _errorMessage.value = "Không thể lưu món ăn đặt trước. Giỏ hàng vẫn được giữ nguyên."
                onComplete(false)
            }
        }
    }

    fun clearMessages() {
        _errorMessage.value = null
    }

    class Factory(
        private val orderRepo: OrderRepository = OrderRepository(),
        private val bookingRepo: BookingRepository = BookingRepository()
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return CartViewModel(orderRepo, bookingRepo) as T
        }
    }
}
