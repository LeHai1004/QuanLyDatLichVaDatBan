package com.ute.quanlydatban.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.firebase.auth.FirebaseAuth
import com.ute.quanlydatban.data.model.Booking
import com.ute.quanlydatban.data.model.DiningTable
import com.ute.quanlydatban.data.model.Restaurant
import com.ute.quanlydatban.data.repository.BookingRepository
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class BookingViewModel(
    private val bookingRepository: BookingRepository = BookingRepository()
) : ViewModel() {

    private val _restaurant = MutableLiveData<Restaurant?>()
    val restaurant: LiveData<Restaurant?> = _restaurant

    private val _selectedDate = MutableLiveData<String>()
    val selectedDate: LiveData<String> = _selectedDate

    private val _selectedTime = MutableLiveData<String>()
    val selectedTime: LiveData<String> = _selectedTime

    private val _selectedGuests = MutableLiveData<Long>(4L)
    val selectedGuests: LiveData<Long> = _selectedGuests

    private val _note = MutableLiveData<String>("")
    val note: LiveData<String> = _note

    private val _tables = MutableLiveData<List<DiningTable>>(emptyList())
    val tables: LiveData<List<DiningTable>> = _tables

    private val _selectedFloor = MutableLiveData<Long>(1L)
    val selectedFloor: LiveData<Long> = _selectedFloor

    private val _selectedTable = MutableLiveData<DiningTable?>()
    val selectedTable: LiveData<DiningTable?> = _selectedTable

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private val _bookingSuccess = MutableLiveData<Booking?>()
    val bookingSuccess: LiveData<Booking?> = _bookingSuccess

    private val _conflictMessage = MutableLiveData<String?>()
    val conflictMessage: LiveData<String?> = _conflictMessage

    fun initRestaurant(
        restaurantId: String,
        name: String? = null,
        address: String? = null,
        openTime: String? = null,
        closeTime: String? = null,
        deposit: Long? = null
    ) {
        if (restaurantId.isBlank()) return

        // Nếu đã có sẵn dữ liệu từ Intent thì khởi tạo trước
        if (!name.isNullOrBlank()) {
            _restaurant.value = Restaurant(
                id = restaurantId,
                name = name,
                address = address.orEmpty(),
                openTime = openTime ?: "09:00",
                closeTime = closeTime ?: "22:00",
                depositAmount = deposit ?: BookingRepository.DEFAULT_DEPOSIT_AMOUNT
            )
        }

        // Đồng thời tải đầy đủ từ Firestore
        _isLoading.value = true
        bookingRepository.getRestaurant(restaurantId) { result ->
            _isLoading.value = false
            result.onSuccess { res ->
                if (res != null) {
                    _restaurant.value = res
                }
            }.onFailure { error ->
                Log.e("BookingViewModel", "Lỗi getRestaurant: ${error.message}", error)
            }
        }
    }

    fun setDate(dateStr: String) {
        _selectedDate.value = dateStr
    }

    fun setTime(timeStr: String) {
        _selectedTime.value = timeStr
    }

    fun setGuests(guests: Long) {
        _selectedGuests.value = guests
        // Nếu đã chọn bàn mà số khách mới vượt capacity bàn thì bỏ chọn
        val currentTable = _selectedTable.value
        if (currentTable != null && currentTable.capacity < guests) {
            _selectedTable.value = null
        }
    }

    fun setNote(noteText: String) {
        _note.value = noteText
    }

    fun setFloor(floor: Long) {
        _selectedFloor.value = floor
    }

    // Chọn bàn và kiểm tra sức chứa + trạng thái
    fun selectTable(table: DiningTable): Boolean {
        if (table.status == "booked") {
            _errorMessage.value = "Bàn này đã có khách đặt. Vui lòng chọn bàn khác."
            return false
        }

        val guests = _selectedGuests.value ?: 1L
        if (table.capacity < guests) {
            _errorMessage.value = "Số người ($guests khách) vượt quá sức chứa của ${table.name} (${table.capacity} khách)."
            return false
        }

        _selectedTable.value = table
        return true
    }

    // Kiểm tra tính hợp lệ của Bước 1 (ngày, giờ, mở cửa)
    fun validateStep1(): Boolean {
        val date = _selectedDate.value
        val time = _selectedTime.value
        val res = _restaurant.value

        if (date.isNullOrBlank()) {
            _errorMessage.value = "Vui lòng chọn ngày đặt bàn."
            return false
        }

        if (time.isNullOrBlank()) {
            _errorMessage.value = "Vui lòng chọn giờ đặt bàn."
            return false
        }

        // Kiểm tra thời gian không được ở quá khứ
        try {
            val format = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            val parsed = format.parse("$date $time") ?: return false
            val selectedCalendar = Calendar.getInstance().apply { setTime(parsed) }
            if (selectedCalendar.before(Calendar.getInstance())) {
                _errorMessage.value = "Vui lòng chọn ngày và giờ trong tương lai."
                return false
            }
        } catch (e: Exception) {
            Log.e("BookingViewModel", "Lỗi parse ngày giờ: ${e.message}")
        }

        // Kiểm tra giờ mở cửa
        if (res != null && res.openTime.isNotBlank() && res.closeTime.isNotBlank()) {
            val selectedMinutes = parseTimeToMinutes(time)
            val openMinutes = parseTimeToMinutes(res.openTime)
            val closeMinutes = parseTimeToMinutes(res.closeTime)

            if (selectedMinutes != null && openMinutes != null && closeMinutes != null) {
                if (selectedMinutes < openMinutes || selectedMinutes > closeMinutes) {
                    _errorMessage.value = "Giờ bạn chọn ($time) nằm ngoài giờ mở cửa của quán (${res.openTime} - ${res.closeTime})."
                    return false
                }
            }
        }

        return true
    }

    private fun parseTimeToMinutes(timeStr: String): Int? {
        val parts = timeStr.trim().split(":")
        if (parts.size != 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        return h * 60 + m
    }

    // Tải danh sách bàn của quán
    fun loadTables(restaurantId: String) {
        if (restaurantId.isBlank()) return

        _isLoading.value = true
        _errorMessage.value = null

        bookingRepository.getTables(restaurantId) { result ->
            _isLoading.value = false
            result.onSuccess { tableList ->
                _tables.value = tableList
                // Thiết lập tầng ban đầu nếu chưa có
                val availableFloors = tableList.map { it.floor }.distinct().sorted()
                if (availableFloors.isNotEmpty() && !availableFloors.contains(_selectedFloor.value)) {
                    _selectedFloor.value = availableFloors.first()
                }
            }.onFailure { error ->
                Log.e("BookingViewModel", "Lỗi loadTables: ${error.message}", error)
                _errorMessage.value = "Không thể tải danh sách bàn. Vui lòng thử lại."
            }
        }
    }

    // Xác nhận đặt bàn (gọi Transaction Firestore)
    fun confirmBooking(onSuccess: (Booking) -> Unit) {
        val table = _selectedTable.value
        val res = _restaurant.value
        val date = _selectedDate.value.orEmpty()
        val time = _selectedTime.value.orEmpty()
        val guests = _selectedGuests.value ?: 2L
        val restaurantId = res?.id.orEmpty()

        if (table == null || restaurantId.isBlank()) {
            _errorMessage.value = "Vui lòng chọn bàn trước khi tiếp tục."
            return
        }

        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: "guest"
        val deposit = res?.depositAmount ?: BookingRepository.DEFAULT_DEPOSIT_AMOUNT

        val newBooking = Booking(
            userId = currentUserId,
            restaurantId = restaurantId,
            tableId = table.id,
            date = date,
            time = time,
            guests = guests,
            depositAmount = deposit,
            status = com.ute.quanlydatban.data.model.BookingStatus.PENDING.name,
            createdAt = System.currentTimeMillis()
        )

        _isLoading.value = true
        _errorMessage.value = null

        bookingRepository.createBookingWithTransaction(newBooking) { result ->
            _isLoading.value = false
            result.onSuccess { created ->
                _bookingSuccess.value = created
                onSuccess(created)
            }.onFailure { error ->
                Log.e("BookingViewModel", "Lỗi tạo booking: ${error.message}", error)
                if (error.message?.contains("TABLE_ALREADY_BOOKED") == true) {
                    _conflictMessage.value = "Bàn vừa được người khác đặt. Danh sách đã cập nhật."
                    // Bỏ chọn bàn bị trùng và tải lại danh sách
                    _selectedTable.value = null
                    loadTables(restaurantId)
                } else {
                    _errorMessage.value = "Không thể tạo đơn đặt bàn. Vui lòng thử lại."
                }
            }
        }
    }

    fun clearMessages() {
        _errorMessage.value = null
        _conflictMessage.value = null
    }

    class Factory(
        private val repository: BookingRepository = BookingRepository()
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return BookingViewModel(repository) as T
        }
    }
}
