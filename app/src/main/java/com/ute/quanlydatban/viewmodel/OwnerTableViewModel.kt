package com.ute.quanlydatban.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.firestore.ListenerRegistration
import com.ute.quanlydatban.data.model.Booking
import com.ute.quanlydatban.data.model.BookingStatus
import com.ute.quanlydatban.data.model.DiningTable
import com.ute.quanlydatban.data.model.Restaurant
import com.ute.quanlydatban.data.repository.OwnerRepository
import com.ute.quanlydatban.data.repository.TableRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class OwnerTableItemUiState(
    val table: DiningTable,
    val currentStatus: String, // "available" | "booked" | "serving"
    val activeBooking: Booking? = null
)

class OwnerTableViewModel(
    private val ownerRepo: OwnerRepository = OwnerRepository(),
    private val tableRepo: TableRepository = TableRepository()
) : ViewModel() {

    private val _restaurant = MutableLiveData<Restaurant?>()
    val restaurant: LiveData<Restaurant?> = _restaurant

    private val _selectedFloor = MutableLiveData<Long>(1L)
    val selectedFloor: LiveData<Long> = _selectedFloor

    private val _allTables = MutableLiveData<List<DiningTable>>(emptyList())
    val allTables: LiveData<List<DiningTable>> = _allTables

    private val _todayBookings = MutableLiveData<List<Booking>>(emptyList())
    val todayBookings: LiveData<List<Booking>> = _todayBookings

    private val _displayTables = MutableLiveData<List<OwnerTableItemUiState>>(emptyList())
    val displayTables: LiveData<List<OwnerTableItemUiState>> = _displayTables

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private val _selectedTableDetail = MutableLiveData<OwnerTableItemUiState?>()
    val selectedTableDetail: LiveData<OwnerTableItemUiState?> = _selectedTableDetail

    private var tablesListener: ListenerRegistration? = null
    private var bookingsListener: ListenerRegistration? = null

    fun selectFloor(floor: Long) {
        _selectedFloor.value = floor
        recomputeDisplayTables()
    }

    fun selectItemForDetail(item: OwnerTableItemUiState?) {
        _selectedTableDetail.value = item
    }

    fun loadData() {
        _isLoading.value = true
        _errorMessage.value = null

        ownerRepo.getOwnerRestaurant { result ->
            result.onSuccess { res ->
                _restaurant.value = res
                startRealtimeListeners(res.id)
            }.onFailure { err ->
                _isLoading.value = false
                _errorMessage.value = err.message ?: "Không thể tải thông tin nhà hàng"
            }
        }
    }

    private fun startRealtimeListeners(restaurantId: String) {
        tablesListener?.remove()
        bookingsListener?.remove()

        val todayStr = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())

        tablesListener = tableRepo.listenTables(restaurantId) { result ->
            _isLoading.value = false
            result.onSuccess { tables ->
                _allTables.value = tables
                recomputeDisplayTables()
            }.onFailure { err ->
                _errorMessage.value = err.message ?: "Lỗi tải danh sách bàn"
            }
        }

        bookingsListener = tableRepo.listenTodayBookings(restaurantId, todayStr) { result ->
            result.onSuccess { bookings ->
                _todayBookings.value = bookings
                recomputeDisplayTables()
            }
        }
    }

    fun recomputeDisplayTables() {
        val currentFloor = _selectedFloor.value ?: 1L
        val tables = _allTables.value.orEmpty().filter { it.floor == currentFloor }
        val bookings = _todayBookings.value.orEmpty()

        val uiItems = tables.map { table ->
            // Tìm booking hôm nay của bàn này
            val tableBookings = bookings.filter { it.tableId == table.id }
            val servingBooking = tableBookings.firstOrNull {
                it.status == BookingStatus.CHECKED_IN.name || it.status == BookingStatus.SERVING.name
            }
            val bookedBooking = tableBookings.firstOrNull {
                it.status == BookingStatus.CONFIRMED.name || it.status == BookingStatus.WAITING.name
            }

            val status = when {
                servingBooking != null -> "serving"
                bookedBooking != null -> "booked"
                table.status == "booked" -> "booked"
                else -> "available"
            }

            val activeBooking = servingBooking ?: bookedBooking

            OwnerTableItemUiState(
                table = table,
                currentStatus = status,
                activeBooking = activeBooking
            )
        }

        _displayTables.value = uiItems
    }

    override fun onCleared() {
        super.onCleared()
        tablesListener?.remove()
        bookingsListener?.remove()
    }
}
