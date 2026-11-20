package com.ute.quanlydatban.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import com.ute.quanlydatban.data.model.Booking
import com.ute.quanlydatban.data.model.BookingStatus
import com.ute.quanlydatban.data.model.Restaurant
import com.ute.quanlydatban.data.repository.BookingRepository

class HistoryViewModel(
    private val bookingRepository: BookingRepository = BookingRepository()
) : ViewModel() {

    companion object {
        const val TAB_UPCOMING = 0
        const val TAB_COMPLETED = 1
        const val TAB_CANCELLED = 2
    }

    private val _currentTab = MutableLiveData<Int>(TAB_UPCOMING)
    val currentTab: LiveData<Int> = _currentTab

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private val _allBookings = MutableLiveData<List<Booking>>(emptyList())
    val allBookings: LiveData<List<Booking>> = _allBookings

    private val _upcomingBookings = MutableLiveData<List<Booking>>(emptyList())
    val upcomingBookings: LiveData<List<Booking>> = _upcomingBookings

    private val _completedBookings = MutableLiveData<List<Booking>>(emptyList())
    val completedBookings: LiveData<List<Booking>> = _completedBookings

    private val _cancelledBookings = MutableLiveData<List<Booking>>(emptyList())
    val cancelledBookings: LiveData<List<Booking>> = _cancelledBookings

    // Cache tên nhà hàng theo restaurantId
    private val _restaurantsCache = MutableLiveData<Map<String, Restaurant>>(emptyMap())
    val restaurantsCache: LiveData<Map<String, Restaurant>> = _restaurantsCache

    private var listenerRegistration: ListenerRegistration? = null

    fun selectTab(tabIndex: Int) {
        _currentTab.value = tabIndex
    }

    fun loadUserBookings() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId.isNullOrBlank()) {
            _allBookings.value = emptyList()
            _upcomingBookings.value = emptyList()
            _completedBookings.value = emptyList()
            _cancelledBookings.value = emptyList()
            return
        }

        _isLoading.value = true
        _errorMessage.value = null

        listenerRegistration?.remove()
        listenerRegistration = bookingRepository.listenWhere("userId", userId) { result ->
            _isLoading.value = false
            result.onSuccess { list ->
                // Sắp xếp đơn mới nhất lên đầu theo createdAt
                val sorted = list.sortedByDescending { it.createdAt }
                _allBookings.value = sorted
                partitionBookings(sorted)
                fetchRestaurantInfo(sorted)
            }.onFailure { error ->
                _errorMessage.value = error.message ?: "Không thể tải lịch sử đặt bàn"
            }
        }
    }

    fun partitionBookings(list: List<Booking>) {
        val upcoming = mutableListOf<Booking>()
        val completed = mutableListOf<Booking>()
        val cancelled = mutableListOf<Booking>()

        for (booking in list) {
            when (booking.status) {
                BookingStatus.PENDING.name,
                BookingStatus.WAITING.name,
                BookingStatus.CONFIRMED.name,
                BookingStatus.CHECKED_IN.name,
                BookingStatus.SERVING.name -> upcoming.add(booking)

                BookingStatus.PAID.name,
                BookingStatus.COMPLETED.name -> completed.add(booking)

                BookingStatus.CANCELLED.name,
                BookingStatus.REJECTED.name,
                BookingStatus.EXPIRED.name,
                BookingStatus.NO_SHOW.name -> cancelled.add(booking)

                else -> upcoming.add(booking)
            }
        }

        _upcomingBookings.value = upcoming
        _completedBookings.value = completed
        _cancelledBookings.value = cancelled
    }

    private fun fetchRestaurantInfo(bookings: List<Booking>) {
        val neededIds = bookings.map { it.restaurantId }.filter { it.isNotBlank() }.distinct()
        val currentCache = _restaurantsCache.value.orEmpty().toMutableMap()
        val missingIds = neededIds.filter { !currentCache.containsKey(it) }

        if (missingIds.isEmpty()) return

        for (resId in missingIds) {
            bookingRepository.getRestaurant(resId) { resResult ->
                resResult.onSuccess { restaurant ->
                    if (restaurant != null) {
                        currentCache[resId] = restaurant
                        _restaurantsCache.value = currentCache
                    }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        listenerRegistration?.remove()
    }
}
