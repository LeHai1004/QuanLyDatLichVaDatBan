package com.ute.quanlydatban.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.ute.quanlydatban.data.model.Booking
import com.ute.quanlydatban.data.model.Payment
import com.ute.quanlydatban.data.model.Restaurant
import com.ute.quanlydatban.data.repository.CheckoutData
import com.ute.quanlydatban.data.repository.CheckoutRepository
import com.ute.quanlydatban.data.repository.OwnerRepository

class OwnerCheckoutViewModel(
    private val checkoutRepo: CheckoutRepository = CheckoutRepository(),
    private val ownerRepo: OwnerRepository = OwnerRepository()
) : ViewModel() {

    // Thông tin quán chủ sở hữu
    private val _restaurant = MutableLiveData<Restaurant?>()
    val restaurant: LiveData<Restaurant?> = _restaurant

    // Màn hình quét vé QR
    private val _isScanProcessing = MutableLiveData<Boolean>(false)
    val isScanProcessing: LiveData<Boolean> = _isScanProcessing

    private val _scanError = MutableLiveData<String?>()
    val scanError: LiveData<String?> = _scanError

    private val _scanSuccessBooking = MutableLiveData<Booking?>()
    val scanSuccessBooking: LiveData<Booking?> = _scanSuccessBooking

    // Màn hình Checkout
    private val _checkoutData = MutableLiveData<CheckoutData?>()
    val checkoutData: LiveData<CheckoutData?> = _checkoutData

    private val _selectedPaymentMethod = MutableLiveData<String>("cash") // "cash" | "vietqr"
    val selectedPaymentMethod: LiveData<String> = _selectedPaymentMethod

    private val _isCheckoutLoading = MutableLiveData<Boolean>(false)
    val isCheckoutLoading: LiveData<Boolean> = _isCheckoutLoading

    private val _checkoutError = MutableLiveData<String?>()
    val checkoutError: LiveData<String?> = _checkoutError

    private val _checkoutSuccessPayment = MutableLiveData<Payment?>()
    val checkoutSuccessPayment: LiveData<Payment?> = _checkoutSuccessPayment

    fun initOwnerRestaurant() {
        ownerRepo.getOwnerRestaurant { result ->
            result.onSuccess { res ->
                _restaurant.value = res
            }
        }
    }

    /**
     * Xử lý mã vé quét được hoặc nhập tay
     */
    fun processTicket(rawTicket: String) {
        if (_isScanProcessing.value == true) return
        val currentRestaurantId = _restaurant.value?.id.orEmpty()

        _isScanProcessing.value = true
        _scanError.value = null

        checkoutRepo.findBookingByTicket(rawTicket) { result ->
            result.onSuccess { booking ->
                if (booking == null) {
                    _isScanProcessing.value = false
                    _scanError.value = "Không tìm thấy thông tin vé đặt bàn trên hệ thống."
                    return@onSuccess
                }

                // Chuyển sang SERVING
                checkoutRepo.transitionToServing(booking.id, currentRestaurantId) { serveResult ->
                    _isScanProcessing.value = false
                    serveResult.onSuccess { updatedBooking ->
                        _scanSuccessBooking.value = updatedBooking
                    }.onFailure { err ->
                        mapScanError(err.message)
                    }
                }
            }.onFailure { err ->
                _isScanProcessing.value = false
                _scanError.value = err.message ?: "Lỗi đọc vé. Vui lòng thử lại."
            }
        }
    }

    private fun mapScanError(errorKey: String?) {
        val message = when (errorKey) {
            "WRONG_RESTAURANT" -> "Vé này không thuộc về quán của bạn."
            "NOT_CHECKED_IN" -> "Khách chưa thực hiện check-in tại quán."
            "ALREADY_SERVING" -> "Vé này đã được xác nhận và đang phục vụ."
            "ALREADY_PAID" -> "Vé này đã hoàn tất thanh toán / kết thúc."
            "BOOKING_CANCELLED" -> "Vé này đã bị hủy hoặc hết hạn sử dụng."
            "BOOKING_NOT_FOUND" -> "Không tìm thấy thông tin vé đặt bàn trên hệ thống."
            else -> errorKey ?: "Xác nhận vé thất bại. Vui lòng thử lại."
        }
        _scanError.value = message
    }

    fun clearScanMessages() {
        _scanError.value = null
        _scanSuccessBooking.value = null
    }

    /**
     * Tải dữ liệu cho màn hình Checkout
     */
    fun loadCheckoutData(bookingId: String) {
        if (bookingId.isBlank()) return
        _isCheckoutLoading.value = true
        _checkoutError.value = null

        checkoutRepo.getCheckoutData(bookingId) { result ->
            _isCheckoutLoading.value = false
            result.onSuccess { data ->
                _checkoutData.value = data
            }.onFailure { err ->
                _checkoutError.value = err.message ?: "Không thể tải thông tin hóa đơn."
            }
        }
    }

    fun selectPaymentMethod(method: String) {
        _selectedPaymentMethod.value = method
    }

    /**
     * Xác nhận thanh toán hóa đơn với khóa double-tap
     */
    fun confirmPayment() {
        if (_isCheckoutLoading.value == true) return
        val data = _checkoutData.value ?: return
        val method = _selectedPaymentMethod.value ?: "cash"

        _isCheckoutLoading.value = true
        _checkoutError.value = null

        checkoutRepo.confirmCheckout(
            bookingId = data.booking.id,
            method = method,
            finalAmount = data.finalAmount
        ) { result ->
            _isCheckoutLoading.value = false
            result.onSuccess { payment ->
                _checkoutSuccessPayment.value = payment
            }.onFailure { err ->
                when (err.message) {
                    "STATUS_CONFLICT" -> {
                        _checkoutError.value = "Đơn đặt bàn chưa ở trạng thái đang phục vụ."
                    }
                    "BOOKING_NOT_FOUND" -> {
                        _checkoutError.value = "Không tìm thấy đơn đặt bàn để thanh toán."
                    }
                    else -> {
                        _checkoutError.value = err.message ?: "Thanh toán thất bại. Vui lòng thử lại."
                    }
                }
            }
        }
    }
}
