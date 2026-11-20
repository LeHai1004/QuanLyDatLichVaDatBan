package com.ute.quanlydatban.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.ute.quanlydatban.data.model.Booking
import com.ute.quanlydatban.data.model.DiningTable
import com.ute.quanlydatban.data.model.Restaurant
import com.ute.quanlydatban.data.repository.CheckInException
import com.ute.quanlydatban.data.repository.CheckInRepository
import com.ute.quanlydatban.data.repository.CheckInResultData

class CheckInViewModel(
    private val repository: CheckInRepository = CheckInRepository()
) : ViewModel() {

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _checkInSuccess = MutableLiveData<CheckInResultData?>()
    val checkInSuccess: LiveData<CheckInResultData?> = _checkInSuccess

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private val _booking = MutableLiveData<Booking?>()
    val booking: LiveData<Booking?> = _booking

    private val _restaurant = MutableLiveData<Restaurant?>()
    val restaurant: LiveData<Restaurant?> = _restaurant

    private val _table = MutableLiveData<DiningTable?>()
    val table: LiveData<DiningTable?> = _table

    /**
     * Thực hiện check-in với mã QR quét được hoặc mã nhập tay
     */
    fun performCheckIn(
        bookingId: String,
        expectedRestaurantId: String,
        qrRawContent: String
    ) {
        if (_isLoading.value == true) return

        _isLoading.value = true
        _errorMessage.value = null

        repository.checkIn(bookingId, expectedRestaurantId, qrRawContent) { result ->
            _isLoading.postValue(false)
            result.onSuccess { data ->
                _booking.postValue(data.booking)
                _checkInSuccess.postValue(data)
            }.onFailure { error ->
                val message = when (error) {
                    is CheckInException -> error.message ?: "Lỗi xác thực mã check-in"
                    else -> error.message ?: "Có lỗi xảy ra khi check-in, vui lòng thử lại."
                }
                _errorMessage.postValue(message)
            }
        }
    }

    /**
     * Tải thông tin đơn đặt bàn, nhà hàng và bàn để hiển thị trên màn hình kết quả
     */
    fun loadDetails(bookingId: String, restaurantId: String) {
        _isLoading.value = true

        repository.getBooking(bookingId) { bookingRes ->
            bookingRes.onSuccess { b ->
                _booking.postValue(b)
                val resId = if (restaurantId.isNotBlank()) restaurantId else (b?.restaurantId ?: "")
                if (resId.isNotBlank()) {
                    repository.getRestaurant(resId) { rRes ->
                        _restaurant.postValue(rRes.getOrNull())
                    }
                }
                val tableId = b?.tableId ?: ""
                if (resId.isNotBlank() && tableId.isNotBlank()) {
                    repository.getTable(resId, tableId) { tRes ->
                        _table.postValue(tRes.getOrNull())
                    }
                }
            }.onFailure { e ->
                _errorMessage.postValue(e.message)
            }
            _isLoading.postValue(false)
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    class Factory(
        private val repository: CheckInRepository = CheckInRepository()
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(CheckInViewModel::class.java)) {
                return CheckInViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
