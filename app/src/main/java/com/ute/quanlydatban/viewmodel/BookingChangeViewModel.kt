package com.ute.quanlydatban.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.ute.quanlydatban.data.model.Booking
import com.ute.quanlydatban.data.model.DiningTable
import com.ute.quanlydatban.data.model.Restaurant
import com.ute.quanlydatban.data.repository.BookingRepository

class BookingChangeViewModel(
    private val bookingRepository: BookingRepository = BookingRepository()
) : ViewModel() {

    private val _booking = MutableLiveData<Booking?>()
    val booking: LiveData<Booking?> = _booking

    private val _restaurant = MutableLiveData<Restaurant?>()
    val restaurant: LiveData<Restaurant?> = _restaurant

    private val _tables = MutableLiveData<List<DiningTable>>(emptyList())
    val tables: LiveData<List<DiningTable>> = _tables

    private val _selectedDate = MutableLiveData<String>()
    val selectedDate: LiveData<String> = _selectedDate

    private val _selectedTime = MutableLiveData<String>()
    val selectedTime: LiveData<String> = _selectedTime

    private val _selectedTable = MutableLiveData<DiningTable?>()
    val selectedTable: LiveData<DiningTable?> = _selectedTable

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private val _changeSuccess = MutableLiveData<Boolean>(false)
    val changeSuccess: LiveData<Boolean> = _changeSuccess

    private val _cancelSuccess = MutableLiveData<Boolean>(false)
    val cancelSuccess: LiveData<Boolean> = _cancelSuccess

    fun loadBooking(bookingId: String) {
        if (bookingId.isBlank()) return
        _isLoading.value = true
        _errorMessage.value = null

        bookingRepository.get(bookingId) { result ->
            _isLoading.value = false
            result.onSuccess { b ->
                _booking.value = b
                if (b != null) {
                    _selectedDate.value = b.date
                    _selectedTime.value = b.time
                    loadRestaurantAndTables(b.restaurantId, b.tableId)
                }
            }.onFailure { err ->
                _errorMessage.value = err.message ?: "Không tìm thấy thông tin đơn đặt bàn"
            }
        }
    }

    private fun loadRestaurantAndTables(restaurantId: String, currentTableId: String) {
        if (restaurantId.isBlank()) return

        bookingRepository.getRestaurant(restaurantId) { resResult ->
            resResult.onSuccess { res ->
                _restaurant.value = res
            }
        }

        bookingRepository.getTables(restaurantId) { tableResult ->
            tableResult.onSuccess { list ->
                _tables.value = list
                // Tìm bàn hiện tại
                val cur = list.firstOrNull { it.id == currentTableId }
                _selectedTable.value = cur
            }
        }
    }

    fun setDate(date: String) {
        _selectedDate.value = date
    }

    fun setTime(time: String) {
        _selectedTime.value = time
    }

    fun selectTable(table: DiningTable) {
        _selectedTable.value = table
    }

    fun submitReschedule() {
        val current = _booking.value ?: return
        val newDate = _selectedDate.value.orEmpty()
        val newTime = _selectedTime.value.orEmpty()
        val table = _selectedTable.value

        if (newDate.isBlank() || newTime.isBlank()) {
            _errorMessage.value = "Vui lòng chọn ngày và giờ mới."
            return
        }

        if (newDate == current.date && newTime == current.time && table?.id == current.tableId) {
            _errorMessage.value = "Vui lòng chọn ngày, giờ hoặc bàn mới khác với lịch hẹn hiện tại."
            return
        }

        _isLoading.value = true
        _errorMessage.value = null

        bookingRepository.rescheduleBooking(
            bookingId = current.id,
            newDate = newDate,
            newTime = newTime,
            newTableId = table?.id
        ) { result ->
            _isLoading.value = false
            result.onSuccess {
                _changeSuccess.value = true
            }.onFailure { err ->
                when (err.message) {
                    "TABLE_CONFLICT" -> {
                        _errorMessage.value = "Bàn này đã có người đặt vào khung giờ đã chọn. Vui lòng chọn giờ hoặc bàn khác!"
                    }
                    "CANNOT_MODIFY_CHECKED_IN" -> {
                        _errorMessage.value = "Đơn đặt bàn đã check-in hoặc đang phục vụ, không thể đổi lịch."
                    }
                    "PAST_APPOINTMENT_TIME" -> {
                        _errorMessage.value = "Đơn đặt bàn đã qua thời gian hẹn, không thể thay đổi."
                    }
                    "PAST_NEW_SCHEDULE" -> {
                        _errorMessage.value = "Thời gian mới phải ở tương lai."
                    }
                    else -> {
                        _errorMessage.value = err.message ?: "Không thể đổi lịch hẹn. Vui lòng thử lại sau."
                    }
                }
            }
        }
    }

    fun submitCancel() {
        val current = _booking.value ?: return
        _isLoading.value = true
        _errorMessage.value = null

        bookingRepository.cancelBookingByCustomer(current.id) { result ->
            _isLoading.value = false
            result.onSuccess {
                _cancelSuccess.value = true
            }.onFailure { err ->
                when (err.message) {
                    "CANNOT_CANCEL_CHECKED_IN" -> {
                        _errorMessage.value = "Đơn đặt bàn đã check-in hoặc đang phục vụ, không thể hủy."
                    }
                    "PAST_APPOINTMENT_TIME" -> {
                        _errorMessage.value = "Đơn đặt bàn đã qua thời gian hẹn, không thể hủy."
                    }
                    else -> {
                        _errorMessage.value = err.message ?: "Hủy đặt bàn thất bại. Vui lòng thử lại."
                    }
                }
            }
        }
    }
}
