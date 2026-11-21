package com.ute.quanlydatban.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.firebase.auth.FirebaseAuth
import com.ute.quanlydatban.data.model.Booking
import com.ute.quanlydatban.data.repository.BookingRepository

data class ActiveBookingDisplay(
    val booking: Booking,
    val restaurantName: String = "",
    val tableName: String = ""
)

class ActiveBookingsViewModel(
    private val bookingRepository: BookingRepository = BookingRepository()
) : ViewModel() {

    private val _activeBookings = MutableLiveData<List<ActiveBookingDisplay>>(emptyList())
    val activeBookings: LiveData<List<ActiveBookingDisplay>> = _activeBookings

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    fun loadActiveBookings() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        if (userId.isBlank()) {
            _activeBookings.value = emptyList()
            return
        }

        _isLoading.value = true
        _errorMessage.value = null

        bookingRepository.cleanupNoShowBookings(userId = userId)
        bookingRepository.getActiveBookingsForCustomer(userId) { result ->
            result.onSuccess { bookings ->
                if (bookings.isEmpty()) {
                    _isLoading.value = false
                    _activeBookings.value = emptyList()
                    return@onSuccess
                }

                val items = mutableListOf<ActiveBookingDisplay>()
                var counter = 0
                val total = bookings.size

                bookings.forEach { b ->
                    bookingRepository.getRestaurant(b.restaurantId) { rResult ->
                        val rName = rResult.getOrNull()?.name.orEmpty().ifEmpty { "Nhà hàng" }
                        if (b.tableId.isNotBlank()) {
                            bookingRepository.getTable(b.restaurantId, b.tableId) { tResult ->
                                val tName = tResult.getOrNull()?.let { "${it.name} · Tầng ${it.floor}" }.orEmpty().ifEmpty { "Bàn đã đặt" }
                                synchronized(items) {
                                    items.add(ActiveBookingDisplay(b, rName, tName))
                                    counter++
                                    if (counter == total) {
                                        _isLoading.postValue(false)
                                        // Giữ nguyên thứ tự ban đầu
                                        val mapOrder = bookings.associateBy { it.id }
                                        val sorted = items.sortedByDescending { mapOrder[it.booking.id]?.createdAt ?: 0L }
                                        _activeBookings.postValue(sorted)
                                    }
                                }
                            }
                        } else {
                            synchronized(items) {
                                items.add(ActiveBookingDisplay(b, rName, "Chưa xếp bàn"))
                                counter++
                                if (counter == total) {
                                    _isLoading.postValue(false)
                                    val mapOrder = bookings.associateBy { it.id }
                                    val sorted = items.sortedByDescending { mapOrder[it.booking.id]?.createdAt ?: 0L }
                                    _activeBookings.postValue(sorted)
                                }
                            }
                        }
                    }
                }
            }.onFailure { err ->
                _isLoading.value = false
                Log.e("ActiveBookingsVM", "Lỗi loadActiveBookings: ${err.message}", err)
                _errorMessage.value = err.message ?: "Không thể tải danh sách đặt bàn."
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    class Factory(
        private val repo: BookingRepository = BookingRepository()
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ActiveBookingsViewModel(repo) as T
        }
    }
}
