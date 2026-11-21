package com.ute.quanlydatban.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.ute.quanlydatban.data.model.Booking
import com.ute.quanlydatban.data.model.BookingStatus
import com.ute.quanlydatban.data.model.OrderItem
import com.ute.quanlydatban.data.model.Restaurant
import com.ute.quanlydatban.data.model.User
import com.ute.quanlydatban.data.repository.BookingRepository
import com.ute.quanlydatban.data.repository.OrderRepository
import com.ute.quanlydatban.data.repository.OwnerRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class BookingWithDetails(
    val booking: Booking,
    val customerName: String = "",
    val customerPhone: String = "",
    val tableName: String = "",
    val roomType: String = "",
    val preOrderItems: List<OrderItem> = emptyList()
)

class OwnerBookingViewModel(
    private val ownerRepo: OwnerRepository = OwnerRepository(),
    private val bookingRepo: BookingRepository = BookingRepository(),
    private val orderRepo: OrderRepository = OrderRepository()
) : ViewModel() {

    companion object {
        private const val TAG = "OwnerBookingViewModel"
        const val TAB_WAITING = 0
        const val TAB_CONFIRMED = 1
        const val TAB_ARRIVED = 2
        const val TAB_CANCELLED = 3
    }

    private val _restaurant = MutableLiveData<Restaurant?>()
    val restaurant: LiveData<Restaurant?> = _restaurant

    private val _selectedTab = MutableLiveData<Int>(TAB_WAITING)
    val selectedTab: LiveData<Int> = _selectedTab

    private val _selectedDate = MutableLiveData<String>()
    val selectedDate: LiveData<String> = _selectedDate

    private val _allBookings = MutableLiveData<List<Booking>>(emptyList())
    val allBookings: LiveData<List<Booking>> = _allBookings

    private val _filteredBookings = MutableLiveData<List<BookingWithDetails>>(emptyList())
    val filteredBookings: LiveData<List<BookingWithDetails>> = _filteredBookings

    // Danh sách món đặt trước của các đơn đang chuẩn bị/phục vụ
    private val _preOrderBookings = MutableLiveData<List<BookingWithDetails>>(emptyList())
    val preOrderBookings: LiveData<List<BookingWithDetails>> = _preOrderBookings

    // Chi tiết 1 đơn
    private val _currentBookingDetail = MutableLiveData<BookingWithDetails?>()
    val currentBookingDetail: LiveData<BookingWithDetails?> = _currentBookingDetail

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private val _actionSuccessMessage = MutableLiveData<String?>()
    val actionSuccessMessage: LiveData<String?> = _actionSuccessMessage

    private var bookingsListenerRegistration: com.google.firebase.firestore.ListenerRegistration? = null

    init {
        val todayStr = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
        _selectedDate.value = todayStr
    }

    fun loadRestaurantAndBookings() {
        _isLoading.value = true
        _errorMessage.value = null

        ownerRepo.getOwnerRestaurant { result ->
            result.onSuccess { res ->
                _restaurant.value = res
                loadBookingsForRestaurant(res.id)
                loadPreOrdersForDate(_selectedDate.value)
            }.onFailure { err ->
                _isLoading.value = false
                Log.e(TAG, "Lỗi getOwnerRestaurant: ${err.message}", err)
                _errorMessage.value = "Không thể tải thông tin quán của bạn."
            }
        }
    }

    fun loadBookingsForRestaurant(restaurantId: String) {
        if (restaurantId.isBlank()) {
            _isLoading.value = false
            return
        }

        _isLoading.value = true
        bookingsListenerRegistration?.remove()
        bookingsListenerRegistration = bookingRepo.listenBookingsForRestaurant(restaurantId) { result ->
            _isLoading.value = false
            result.onSuccess { list ->
                _allBookings.value = list
                applyFilters()
            }.onFailure { err ->
                Log.e(TAG, "Lỗi tải danh sách bookings: ${err.message}", err)
                _errorMessage.value = "Không thể tải danh sách đặt bàn."
            }
        }
    }

    fun selectTab(tabIndex: Int) {
        _selectedTab.value = tabIndex
        applyFilters()
    }

    fun selectDate(dateStr: String) {
        _selectedDate.value = dateStr
        applyFilters()
        loadPreOrdersForDate(dateStr)
    }

    /**
     * Tải danh sách đơn có món đặt trước (source = pre) cho màn hình OwnerPreOrderFragment
     * Lấy các booking của restaurantId theo ngày đang chọn với trạng thái WAITING, CONFIRMED, CHECKED_IN, SERVING.
     * Hoặc nếu targetBookingId != null thì chỉ tải đúng đơn đó (bỏ qua lọc ngày).
     */
    fun loadPreOrdersForDate(targetDateStr: String? = null, targetBookingId: String? = null) {
        val rId = _restaurant.value?.id.orEmpty()
        val chosenDate = targetDateStr ?: _selectedDate.value.orEmpty()

        _isLoading.value = true

        if (!targetBookingId.isNullOrBlank()) {
            // Khi vào từ màn Chi tiết (có bookingId): chỉ hiện món của đơn đó, ưu tiên theo bookingId, bỏ qua lọc ngày
            bookingRepo.get(targetBookingId) { bResult ->
                bResult.onSuccess { booking ->
                    if (booking != null) {
                        orderRepo.getWhere("bookingId", targetBookingId) { oResult ->
                            _isLoading.value = false
                            val allItems = oResult.getOrDefault(emptyList())
                            val preItems = allItems.filter { it.source == "pre" || it.source.isBlank() }
                            Log.d("OwnerPreOrder", "Target booking #${booking.id}: ${preItems.size} món đặt trước")

                            val detail = BookingWithDetails(
                                booking = booking,
                                customerName = "Khách hàng",
                                tableName = booking.tableId.ifBlank { "Bàn chung" },
                                roomType = "Phòng tiêu chuẩn",
                                preOrderItems = preItems
                            )
                            _preOrderBookings.value = listOf(detail)

                            // Bổ sung thông tin User & Table
                            enrichPreOrderList(listOf(detail))
                        }
                    } else {
                        _isLoading.value = false
                        Log.e("OwnerPreOrder", "Không tìm thấy booking #$targetBookingId")
                        _preOrderBookings.value = emptyList()
                    }
                }.onFailure { err ->
                    _isLoading.value = false
                    Log.e("OwnerPreOrder", "Lỗi tải booking #$targetBookingId: ${err.message}", err)
                    _preOrderBookings.value = emptyList()
                }
            }
            return
        }

        // Trường hợp xem danh sách món đặt trước theo ngày
        if (rId.isBlank()) {
            _isLoading.value = false
            return
        }

        val validStatuses = listOf(
            BookingStatus.WAITING.name,
            BookingStatus.CONFIRMED.name,
            BookingStatus.CHECKED_IN.name,
            BookingStatus.SERVING.name
        )

        bookingRepo.getWhere("restaurantId", rId) { bResult ->
            bResult.onSuccess { allBookings ->
                val matchingBookings = allBookings.filter { b ->
                    val statusMatches = b.status.uppercase() in validStatuses
                    val dateMatches = chosenDate.isBlank() || b.date.isBlank() ||
                            b.date.trim() == chosenDate.trim() ||
                            b.date.replace("-", "/") == chosenDate.trim()
                    statusMatches && dateMatches
                }.sortedBy { it.time }

                Log.d("OwnerPreOrder", "Tìm thấy ${matchingBookings.size} bookings phù hợp ngày $chosenDate với các trạng thái $validStatuses")

                if (matchingBookings.isEmpty()) {
                    _isLoading.value = false
                    _preOrderBookings.value = emptyList()
                    return@onSuccess
                }

                // Với mỗi booking, lấy orderItems source = pre
                var pendingCount = matchingBookings.size
                val results = mutableListOf<BookingWithDetails>()

                for (b in matchingBookings) {
                    orderRepo.getWhere("bookingId", b.id) { oResult ->
                        val items = oResult.getOrDefault(emptyList()).filter { it.source == "pre" || it.source.isBlank() }
                        if (items.isNotEmpty()) {
                            results.add(
                                BookingWithDetails(
                                    booking = b,
                                    customerName = "Khách hàng",
                                    tableName = b.tableId.ifBlank { "Bàn chung" },
                                    roomType = "Phòng tiêu chuẩn",
                                    preOrderItems = items
                                )
                            )
                        }

                        pendingCount--
                        if (pendingCount <= 0) {
                            _isLoading.value = false
                            val sortedResults = results.sortedBy { it.booking.time }
                            Log.d("OwnerPreOrder", "Tổng số đơn có món đặt trước: ${sortedResults.size}")
                            _preOrderBookings.value = sortedResults
                            enrichPreOrderList(sortedResults)
                        }
                    }
                }
            }.onFailure { err ->
                _isLoading.value = false
                Log.e("OwnerPreOrder", "Lỗi tải bookings cho PreOrder: ${err.message}", err)
                _preOrderBookings.value = emptyList()
            }
        }
    }

    private fun enrichPreOrderList(list: List<BookingWithDetails>) {
        if (list.isEmpty()) return
        val enriched = ArrayList(list)
        val firestore = FirebaseFirestore.getInstance()
        val rId = _restaurant.value?.id.orEmpty()

        for (i in enriched.indices) {
            val item = enriched[i]
            if (item.booking.userId.isNotBlank()) {
                firestore.collection("users").document(item.booking.userId).get().addOnSuccessListener { uSnap ->
                    val user = uSnap.toObject(User::class.java)
                    if (user != null && i < enriched.size) {
                        enriched[i] = enriched[i].copy(
                            customerName = user.name.ifEmpty { "Khách #${item.booking.id.takeLast(4)}" },
                            customerPhone = user.phone
                        )
                        _preOrderBookings.value = ArrayList(enriched)
                    }
                }
            }

            val tableId = item.booking.tableId
            val resId = if (rId.isNotBlank()) rId else item.booking.restaurantId
            if (resId.isNotBlank() && tableId.isNotBlank()) {
                firestore.collection("restaurants").document(resId)
                    .collection("tables").document(tableId).get().addOnSuccessListener { tSnap ->
                        val tName = tSnap.getString("name") ?: tableId
                        val tType = tSnap.getString("type") ?: "table"
                        val roomLabel = if (tType == "room") "Phòng VIP" else "Khu bàn ăn"
                        if (i < enriched.size) {
                            enriched[i] = enriched[i].copy(
                                tableName = tName,
                                roomType = roomLabel
                            )
                            _preOrderBookings.value = ArrayList(enriched)
                        }
                    }
            }
        }
    }

    private fun applyFilters() {
        val all = _allBookings.value.orEmpty()
        val tab = _selectedTab.value ?: TAB_WAITING
        val chosenDate = _selectedDate.value.orEmpty()

        val matchingStatus = when (tab) {
            TAB_WAITING -> listOf(BookingStatus.WAITING.name, BookingStatus.PENDING.name)
            TAB_CONFIRMED -> listOf(BookingStatus.CONFIRMED.name)
            TAB_ARRIVED -> listOf(BookingStatus.CHECKED_IN.name, BookingStatus.SERVING.name, BookingStatus.PAID.name, BookingStatus.COMPLETED.name)
            TAB_CANCELLED -> listOf(BookingStatus.CANCELLED.name, BookingStatus.REJECTED.name, BookingStatus.EXPIRED.name, BookingStatus.NO_SHOW.name)
            else -> listOf(BookingStatus.WAITING.name)
        }

        val filtered = all.filter { b ->
            val statusMatches = b.status.uppercase() in matchingStatus
            val dateMatches = chosenDate.isBlank() || b.date.isBlank() || b.date == chosenDate || b.date.replace("-", "/") == chosenDate
            statusMatches && dateMatches
        }.sortedBy { it.time }

        // Nạp thêm chi tiết user & bàn cho từng booking
        enrichBookings(filtered)
    }

    private fun enrichBookings(bookings: List<Booking>) {
        if (bookings.isEmpty()) {
            _filteredBookings.value = emptyList()
            return
        }

        val rId = _restaurant.value?.id.orEmpty()
        val firestore = FirebaseFirestore.getInstance()

        // Đọc thông tin user & table
        val enrichedList = bookings.map { b ->
            val customerName = if (b.userId.isNotBlank()) "Khách hàng" else "Khách vãng lai"
            val tableName = if (b.tableId.isNotBlank()) b.tableId else "Bàn chung"
            BookingWithDetails(
                booking = b,
                customerName = customerName,
                tableName = tableName,
                roomType = "Phòng tiêu chuẩn"
            )
        }.toMutableList()

        _filteredBookings.value = enrichedList

        // Query song song để lấy tên thật của User và Table
        for (i in bookings.indices) {
            val b = bookings[i]
            if (b.userId.isNotBlank()) {
                firestore.collection("users").document(b.userId).get().addOnSuccessListener { uSnap ->
                    val user = uSnap.toObject(User::class.java)
                    if (user != null && i < enrichedList.size) {
                        enrichedList[i] = enrichedList[i].copy(
                            customerName = user.name.ifEmpty { "Khách #${b.id.takeLast(4)}" },
                            customerPhone = user.phone
                        )
                        _filteredBookings.value = ArrayList(enrichedList)
                    }
                }
            }

            if (rId.isNotBlank() && b.tableId.isNotBlank()) {
                firestore.collection("restaurants").document(rId)
                    .collection("tables").document(b.tableId).get().addOnSuccessListener { tSnap ->
                        val tName = tSnap.getString("name") ?: b.tableId
                        val tType = tSnap.getString("type") ?: "table"
                        val roomLabel = if (tType == "room") "Phòng VIP" else "Khu bàn ăn"
                        if (i < enrichedList.size) {
                            enrichedList[i] = enrichedList[i].copy(
                                tableName = tName,
                                roomType = roomLabel
                            )
                            _filteredBookings.value = ArrayList(enrichedList)
                        }
                    }
            }
        }
    }

    private var bookingDetailListener: ListenerRegistration? = null

    fun startListeningBooking(bookingId: String) {
        if (bookingId.isBlank()) return
        _isLoading.value = true
        _errorMessage.value = null

        bookingDetailListener?.remove()
        bookingDetailListener = bookingRepo.observeBooking(
            bookingId = bookingId,
            onChange = { booking ->
                _isLoading.value = false
                if (booking != null) {
                    loadBookingDetailInternal(booking)
                } else {
                    _errorMessage.value = "Không tìm thấy đơn đặt bàn #$bookingId"
                }
            },
            onError = { err ->
                _isLoading.value = false
                _errorMessage.value = err.message ?: "Lỗi khi tải thông tin đơn đặt bàn."
            }
        )
    }

    fun stopListeningBooking() {
        bookingDetailListener?.remove()
        bookingDetailListener = null
    }

    private fun loadBookingDetailInternal(booking: Booking) {
        orderRepo.getWhere("bookingId", booking.id) { oResult ->
            val orderItems = oResult.getOrDefault(emptyList())

            val firestore = FirebaseFirestore.getInstance()
            val cName = "Khách hàng"
            val cPhone = "090 123 4567"

            val initialDetail = BookingWithDetails(
                booking = booking,
                customerName = cName,
                customerPhone = cPhone,
                tableName = booking.tableId,
                roomType = "Bàn ăn",
                preOrderItems = orderItems
            )
            _currentBookingDetail.value = initialDetail

            if (booking.userId.isNotBlank()) {
                firestore.collection("users").document(booking.userId).get().addOnSuccessListener { uSnap ->
                    val u = uSnap.toObject(User::class.java)
                    if (u != null) {
                        _currentBookingDetail.value = _currentBookingDetail.value?.copy(
                            customerName = u.name.ifEmpty { "Khách #${booking.id.takeLast(4)}" },
                            customerPhone = u.phone.ifEmpty { "090 123 4567" }
                        )
                    }
                }
            }

            if (booking.restaurantId.isNotBlank() && booking.tableId.isNotBlank()) {
                firestore.collection("restaurants").document(booking.restaurantId)
                    .collection("tables").document(booking.tableId).get().addOnSuccessListener { tSnap ->
                        val tName = tSnap.getString("name") ?: booking.tableId
                        val tType = tSnap.getString("type") ?: "table"
                        _currentBookingDetail.value = _currentBookingDetail.value?.copy(
                            tableName = tName,
                            roomType = if (tType == "room") "Phòng VIP" else "Khu bàn ăn"
                        )
                    }
            }
        }
    }

    // Tải chi tiết 1 đơn cụ thể
    fun loadBookingDetail(bookingId: String) {
        startListeningBooking(bookingId)
    }

    // Xác nhận đơn
    fun confirmBooking(bookingId: String, onFinished: (Boolean) -> Unit) {
        _isLoading.value = true
        _errorMessage.value = null

        bookingRepo.confirmBookingByOwner(bookingId) { result ->
            _isLoading.value = false
            result.onSuccess {
                _actionSuccessMessage.value = "Đã xác nhận đơn đặt bàn thành công."
                // Làm mới danh sách
                val rId = _restaurant.value?.id.orEmpty()
                if (rId.isNotBlank()) loadBookingsForRestaurant(rId)
                if (_currentBookingDetail.value?.booking?.id == bookingId) loadBookingDetail(bookingId)
                onFinished(true)
            }.onFailure { error ->
                Log.e(TAG, "Lỗi confirmBooking: ${error.message}", error)
                if (error.message?.contains("STATUS_CONFLICT") == true) {
                    _errorMessage.value = "Trạng thái đơn đã thay đổi bởi khách hoặc hệ thống. Đang tải lại."
                    val rId = _restaurant.value?.id.orEmpty()
                    if (rId.isNotBlank()) loadBookingsForRestaurant(rId)
                } else {
                    _errorMessage.value = "Xác nhận đơn thất bại. Vui lòng thử lại."
                }
                onFinished(false)
            }
        }
    }

    // Từ chối đơn & hoàn cọc
    fun rejectBooking(bookingId: String, onFinished: (Boolean) -> Unit) {
        _isLoading.value = true
        _errorMessage.value = null

        bookingRepo.rejectBookingByOwner(bookingId) { result ->
            _isLoading.value = false
            result.onSuccess {
                _actionSuccessMessage.value = "Đã từ chối đơn đặt bàn và tạo lệnh hoàn cọc."
                val rId = _restaurant.value?.id.orEmpty()
                if (rId.isNotBlank()) loadBookingsForRestaurant(rId)
                if (_currentBookingDetail.value?.booking?.id == bookingId) loadBookingDetail(bookingId)
                onFinished(true)
            }.onFailure { error ->
                Log.e(TAG, "Lỗi rejectBooking: ${error.message}", error)
                if (error.message?.contains("STATUS_CONFLICT") == true) {
                    _errorMessage.value = "Trạng thái đơn đã thay đổi bởi khách hoặc hệ thống. Đang tải lại."
                    val rId = _restaurant.value?.id.orEmpty()
                    if (rId.isNotBlank()) loadBookingsForRestaurant(rId)
                } else {
                    _errorMessage.value = "Từ chối đơn thất bại. Vui lòng thử lại."
                }
                onFinished(false)
            }
        }
    }

    fun clearMessages() {
        _errorMessage.value = null
        _actionSuccessMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        bookingsListenerRegistration?.remove()
        bookingsListenerRegistration = null
        stopListeningBooking()
    }

    class Factory(
        private val ownerRepo: OwnerRepository = OwnerRepository(),
        private val bookingRepo: BookingRepository = BookingRepository(),
        private val orderRepo: OrderRepository = OrderRepository()
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return OwnerBookingViewModel(ownerRepo, bookingRepo, orderRepo) as T
        }
    }
}
