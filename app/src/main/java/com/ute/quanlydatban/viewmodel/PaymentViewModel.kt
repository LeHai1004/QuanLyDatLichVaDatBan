package com.ute.quanlydatban.viewmodel

import android.os.CountDownTimer
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.firebase.auth.FirebaseAuth
import com.ute.quanlydatban.data.model.Booking
import com.ute.quanlydatban.data.model.BookingStatus
import com.ute.quanlydatban.data.model.OrderItem
import com.ute.quanlydatban.data.model.Restaurant
import com.ute.quanlydatban.data.model.Voucher
import com.ute.quanlydatban.data.repository.BookingRepository
import com.ute.quanlydatban.data.repository.OrderRepository
import com.ute.quanlydatban.data.repository.PaymentRepository
import com.ute.quanlydatban.data.repository.VoucherRepository
import java.util.Locale

class PaymentViewModel(
    private val paymentRepository: PaymentRepository = PaymentRepository(),
    private val bookingRepository: BookingRepository = BookingRepository(),
    private val orderRepository: OrderRepository = OrderRepository()
) : ViewModel() {

    companion object {
        private const val TAG = "PaymentViewModel"
        const val HOLD_TIME_MS = 10 * 60 * 1000L // 10 phút
    }

    private val _booking = MutableLiveData<Booking?>()
    val booking: LiveData<Booking?> = _booking

    private val _restaurant = MutableLiveData<Restaurant?>()
    val restaurant: LiveData<Restaurant?> = _restaurant

    private val _orderItems = MutableLiveData<List<OrderItem>>(emptyList())
    val orderItems: LiveData<List<OrderItem>> = _orderItems

    private val voucherRepository = VoucherRepository()

    private val _selectedVoucher = MutableLiveData<Voucher?>()
    val selectedVoucher: LiveData<Voucher?> = _selectedVoucher

    private val _voucherDiscount = MutableLiveData<Long>(0L)
    val voucherDiscount: LiveData<Long> = _voucherDiscount

    private val _remainingTimeFormatted = MutableLiveData<String>("10:00")
    val remainingTimeFormatted: LiveData<String> = _remainingTimeFormatted

    private val _isExpired = MutableLiveData<Boolean>(false)
    val isExpired: LiveData<Boolean> = _isExpired

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private val _isPaymentSuccess = MutableLiveData<Boolean>(false)
    val isPaymentSuccess: LiveData<Boolean> = _isPaymentSuccess

    private var countDownTimer: CountDownTimer? = null

    fun loadData(bookingId: String, restaurantId: String) {
        if (bookingId.isBlank()) {
            _errorMessage.value = "Không tìm thấy mã đơn đặt bàn."
            return
        }

        _isLoading.value = true
        _errorMessage.value = null

        // Tải thông tin đơn đặt bàn
        paymentRepository.getBooking(bookingId) { result ->
            result.onSuccess { b ->
                _booking.value = b
                if (b != null) {
                    val rId = if (restaurantId.isNotBlank()) restaurantId else b.restaurantId
                    if (rId.isNotBlank()) {
                        loadRestaurant(rId)
                    }
                    startCountdown(b.createdAt)
                }
            }.onFailure { error ->
                Log.e(TAG, "Lỗi tải booking: ${error.message}", error)
                _errorMessage.value = "Không thể tải thông tin đơn đặt bàn."
            }
        }

        // Tải các món đã đặt trước
        orderRepository.getWhere("bookingId", bookingId) { orderResult ->
            _isLoading.value = false
            orderResult.onSuccess { items ->
                _orderItems.value = items
            }.onFailure { err ->
                Log.e(TAG, "Lỗi tải orderItems: ${err.message}", err)
            }
        }
    }

    private fun loadRestaurant(rId: String) {
        bookingRepository.getRestaurant(rId) { resResult ->
            resResult.onSuccess { res ->
                _restaurant.value = res
            }.onFailure { error ->
                Log.e(TAG, "Lỗi tải restaurant: ${error.message}", error)
            }
        }
    }

    /**
     * Bắt đầu đếm ngược 10 phút tính từ booking.createdAt.
     * Không bị reset lại khi mở lại màn hình.
     */
    fun startCountdown(createdAt: Long) {
        countDownTimer?.cancel()

        val now = System.currentTimeMillis()
        val safeCreatedAt = if (createdAt > 0) createdAt else now
        val expireTime = safeCreatedAt + HOLD_TIME_MS
        val remainingMs = expireTime - now

        if (remainingMs <= 0) {
            _remainingTimeFormatted.value = "00:00"
            handleExpired()
            return
        }

        countDownTimer = object : CountDownTimer(remainingMs, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val minutes = (millisUntilFinished / 1000) / 60
                val seconds = (millisUntilFinished / 1000) % 60
                _remainingTimeFormatted.value = String.format(Locale.US, "%02d:%02d", minutes, seconds)
            }

            override fun onFinish() {
                _remainingTimeFormatted.value = "00:00"
                handleExpired()
            }
        }.start()
    }

    private fun handleExpired() {
        val currentBooking = _booking.value
        if (currentBooking != null && currentBooking.status == BookingStatus.PENDING.name) {
            _isExpired.value = true
            paymentRepository.markBookingExpired(currentBooking.id) { result ->
                result.onSuccess {
                    Log.i(TAG, "Đã cập nhật booking #${currentBooking.id} sang EXPIRED")
                }
            }
        }
    }

    /**
     * Xác nhận đã chuyển khoản cọc (Demo thủ công hoặc nhận kết quả)
     */
    fun confirmDeposit(bookingId: String, amount: Long) {
        if (_isExpired.value == true) {
            _errorMessage.value = "Thời gian giữ bàn đã hết. Vui lòng đặt lại bàn mới."
            return
        }

        val userId = FirebaseAuth.getInstance().currentUser?.uid.orEmpty().ifEmpty { "GUEST_USER" }
        val voucherId = _selectedVoucher.value?.id.orEmpty()
        _isLoading.value = true
        _errorMessage.value = null

        paymentRepository.confirmDepositPayment(bookingId, userId, amount) { result ->
            _isLoading.value = false
            result.onSuccess {
                if (voucherId.isNotBlank()) {
                    VoucherRepository().useVoucherTransaction(userId, voucherId, bookingId) { _ -> }
                }
                countDownTimer?.cancel()
                _isPaymentSuccess.value = true
            }.onFailure { error ->
                Log.e(TAG, "Lỗi confirmDeposit: ${error.message}", error)
                if (error.message?.contains("BOOKING_EXPIRED") == true) {
                    _isExpired.value = true
                    _errorMessage.value = "Thời gian giữ bàn đã hết hạn."
                } else {
                    _errorMessage.value = "Thanh toán cọc thất bại. Vui lòng thử lại."
                }
            }
        }
    }

    fun applyVoucher(voucher: Voucher) {
        _selectedVoucher.value = voucher
        val raw = calculateRawTotalBill()
        val discount = voucherRepository.calculateDiscount(voucher, raw)
        _voucherDiscount.value = discount
    }

    fun applyVoucherByCode(code: String) {
        val raw = calculateRawTotalBill()
        val userId = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        val restId = _restaurant.value?.id.orEmpty()
        voucherRepository.applyVoucherCode(userId, code, raw, restId) { result ->
            result.onSuccess { voucher ->
                applyVoucher(voucher)
            }.onFailure { err ->
                _errorMessage.value = err.message ?: "Mã voucher không hợp lệ."
            }
        }
    }

    fun calculateRawTotalBill(): Long {
        val itemsTotal = _orderItems.value?.sumOf { it.qty * it.unitPrice } ?: 0L
        val deposit = getDepositAmount()
        return if (itemsTotal > 0L) itemsTotal else deposit
    }

    /**
     * Tính tổng hóa đơn (tiền món + cọc/phụ thu nếu có, đã trừ voucher)
     */
    fun calculateTotalBill(): Long {
        val raw = calculateRawTotalBill()
        val discount = _voucherDiscount.value ?: 0L
        val net = raw - discount
        return if (net > 0L) net else 0L
    }

    fun getDepositAmount(): Long {
        val bookingDeposit = _booking.value?.depositAmount ?: 0L
        if (bookingDeposit > 0L) return bookingDeposit
        val restDeposit = _restaurant.value?.depositAmount ?: 0L
        if (restDeposit > 0L) return restDeposit
        return 100_000L
    }

    fun calculateRemainingAmount(): Long {
        val total = calculateTotalBill()
        val deposit = getDepositAmount()
        val rem = total - deposit
        return if (rem > 0L) rem else 0L
    }

    fun clearMessages() {
        _errorMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        countDownTimer?.cancel()
    }

    class Factory(
        private val paymentRepo: PaymentRepository = PaymentRepository(),
        private val bookingRepo: BookingRepository = BookingRepository(),
        private val orderRepo: OrderRepository = OrderRepository()
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PaymentViewModel(paymentRepo, bookingRepo, orderRepo) as T
        }
    }
}
