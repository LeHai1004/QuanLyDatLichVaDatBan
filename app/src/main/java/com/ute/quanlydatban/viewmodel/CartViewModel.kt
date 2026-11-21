package com.ute.quanlydatban.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.firebase.auth.FirebaseAuth
import com.ute.quanlydatban.data.model.MenuItem
import com.ute.quanlydatban.data.model.OrderItem
import com.ute.quanlydatban.data.model.OrderItemStatus
import com.ute.quanlydatban.data.model.Restaurant
import com.ute.quanlydatban.data.model.Voucher
import com.ute.quanlydatban.data.repository.BookingRepository
import com.ute.quanlydatban.data.repository.OrderRepository
import com.ute.quanlydatban.data.repository.VoucherRepository

data class CartItemUiModel(
    val menuItem: MenuItem,
    val quantity: Long,
    val unitPrice: Long = menuItem.price
) {
    val totalPrice: Long get() = quantity * unitPrice
}

class CartViewModel(
    private val orderRepository: OrderRepository = OrderRepository(),
    private val bookingRepository: BookingRepository = BookingRepository(),
    private val voucherRepository: VoucherRepository = VoucherRepository()
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

    private val _appliedVoucher = MutableLiveData<Voucher?>()
    val appliedVoucher: LiveData<Voucher?> = _appliedVoucher

    private val _discountAmount = MutableLiveData<Long>(0L)
    val discountAmount: LiveData<Long> = _discountAmount

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
        if (bId.isNotBlank()) bookingId = bId
        if (rId.isNotBlank()) restaurantId = rId
        if (!tName.isNullOrBlank()) tableName = tName
        if (!date.isNullOrBlank()) bookingDate = date
        if (!time.isNullOrBlank()) bookingTime = time
        if (guests != null && guests > 0) bookingGuests = guests

        if (!rName.isNullOrBlank() && (_restaurant.value == null || _restaurant.value?.name.isNullOrBlank())) {
            _restaurant.value = Restaurant(
                id = rId,
                name = rName,
                depositAmount = deposit ?: 100_000L
            )
        }

        if (restaurantId.isNotBlank()) {
            bookingRepository.getRestaurant(restaurantId) { resResult ->
                resResult.onSuccess { res ->
                    if (res != null) {
                        _restaurant.value = res
                    }
                }
            }
            if (_menuItems.value.isNullOrEmpty()) {
                loadMenu(restaurantId)
            }
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
            current[item.id] = CartItemUiModel(
                menuItem = item,
                quantity = 1,
                unitPrice = item.price
            )
        }
        _cartItems.value = current
        revalidateVoucher()
        return true
    }

    fun decreaseItem(menuItemId: String) {
        val current = _cartItems.value?.toMutableMap() ?: return
        val existing = current[menuItemId] ?: return
        if (existing.quantity > 1) {
            current[menuItemId] = existing.copy(quantity = existing.quantity - 1)
        } else {
            current.remove(menuItemId)
        }
        _cartItems.value = current
        revalidateVoucher()
    }

    fun removeItem(menuItemId: String) {
        val current = _cartItems.value?.toMutableMap() ?: return
        current.remove(menuItemId)
        _cartItems.value = current
        revalidateVoucher()
    }

    private fun revalidateVoucher() {
        val voucher = _appliedVoucher.value ?: return
        val subtotal = getSubtotal()
        if (subtotal < voucher.minOrder) {
            _appliedVoucher.value = null
            _discountAmount.value = 0L
            _errorMessage.value = "Tạm tính đơn hàng chưa đạt điều kiện tối thiểu của voucher."
        } else {
            val discount = voucherRepository.calculateDiscount(voucher, subtotal)
            _discountAmount.value = discount
        }
    }

    fun applyVoucherCode(code: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        val subtotal = getSubtotal()
        _isLoading.value = true
        _errorMessage.value = null

        voucherRepository.applyVoucherCode(userId, code, subtotal, restaurantId) { result ->
            _isLoading.value = false
            result.onSuccess { voucher ->
                val discount = voucherRepository.calculateDiscount(voucher, subtotal)
                _appliedVoucher.value = voucher
                _discountAmount.value = discount
            }.onFailure { err ->
                _errorMessage.value = err.message ?: "Mã voucher không hợp lệ."
            }
        }
    }

    fun removeVoucher() {
        _appliedVoucher.value = null
        _discountAmount.value = 0L
    }

    fun getTotalQuantity(): Long {
        return _cartItems.value?.values?.sumOf { it.quantity } ?: 0L
    }

    fun getSubtotal(): Long {
        return _cartItems.value?.values?.sumOf { it.totalPrice } ?: 0L
    }

    fun getDiscount(): Long {
        return _discountAmount.value ?: 0L
    }

    fun getFinalTotal(): Long {
        return maxOf(0L, getSubtotal() - getDiscount())
    }

    fun getDeposit(): Long {
        return _restaurant.value?.depositAmount ?: 100_000L
    }

    fun submitPreOrder(onComplete: (Boolean) -> Unit) {
        val currentCart = _cartItems.value.orEmpty()

        if (bookingId.isBlank() || restaurantId.isBlank()) {
            _errorMessage.value = "Thiếu thông tin đơn đặt bàn hoặc nhà hàng."
            onComplete(false)
            return
        }

        if (currentCart.isEmpty()) {
            onComplete(true)
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
        private val bookingRepo: BookingRepository = BookingRepository(),
        private val voucherRepo: VoucherRepository = VoucherRepository()
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return CartViewModel(orderRepo, bookingRepo, voucherRepo) as T
        }
    }
}
