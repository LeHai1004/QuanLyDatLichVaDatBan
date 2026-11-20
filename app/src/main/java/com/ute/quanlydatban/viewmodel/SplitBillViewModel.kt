package com.ute.quanlydatban.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.ute.quanlydatban.data.model.Booking
import com.ute.quanlydatban.data.model.BookingGuest
import com.ute.quanlydatban.data.model.OrderItem
import com.ute.quanlydatban.data.repository.SplitBillCalculator
import com.ute.quanlydatban.data.repository.SplitBillRepository
import com.ute.quanlydatban.ui.customer.split.SplitPersonUiModel

class SplitBillViewModel(
    private val repository: SplitBillRepository = SplitBillRepository()
) : ViewModel() {

    companion object {
        const val MODE_EQUAL = "EQUAL"
        const val MODE_ITEMS = "ITEMS"

        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SplitBillViewModel() as T
            }
        }
    }

    private var currentBookingId: String = ""
    private var resolvedBooking: Booking? = null

    private val _mode = MutableLiveData<String>(MODE_EQUAL)
    val mode: LiveData<String> = _mode

    private val _totalPayable = MutableLiveData<Long>(0L)
    val totalPayable: LiveData<Long> = _totalPayable

    private val _orderItems = MutableLiveData<List<OrderItem>>(emptyList())
    val orderItems: LiveData<List<OrderItem>> = _orderItems

    private val _guestCount = MutableLiveData<Int>(2)
    val guestCount: LiveData<Int> = _guestCount

    private val _persons = MutableLiveData<List<SplitPersonUiModel>>(emptyList())
    val persons: LiveData<List<SplitPersonUiModel>> = _persons

    private val _remainderNotice = MutableLiveData<String?>()
    val remainderNotice: LiveData<String?> = _remainderNotice

    private val _itemAssignments = MutableLiveData<Map<String, Int>>(emptyMap())
    val itemAssignments: LiveData<Map<String, Int>> = _itemAssignments

    private val _itemAssignError = MutableLiveData<String?>()
    val itemAssignError: LiveData<String?> = _itemAssignError

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private val _allPaid = MutableLiveData<Boolean>(false)
    val allPaid: LiveData<Boolean> = _allPaid

    fun initialize(bookingId: String, initialTotal: Long) {
        currentBookingId = bookingId
        _isLoading.value = true
        _errorMessage.value = null

        repository.loadBookingAndOrderItems(bookingId) { result ->
            result.onSuccess { (booking, items) ->
                resolvedBooking = booking
                _orderItems.postValue(items)

                var finalTotal = initialTotal
                if (finalTotal <= 0L) {
                    var totalDishes = 0L
                    for (item in items) {
                        totalDishes += (item.qty * item.unitPrice)
                    }
                    val deposit = if (booking.depositAmount > 0) booking.depositAmount else 100_000L
                    finalTotal = maxOf(0L, totalDishes - deposit)
                }
                _totalPayable.postValue(finalTotal)

                // Load existing bookingGuests or initialize default
                repository.getExistingGuests(bookingId) { guestResult ->
                    _isLoading.postValue(false)
                    val existingGuests = guestResult.getOrDefault(emptyList())

                    val count = if (existingGuests.size >= 2) existingGuests.size else 2
                    _guestCount.postValue(count)

                    // Default assignments: assign dishes round-robin or to Person 0
                    val defaultAssignments = mutableMapOf<String, Int>()
                    items.forEachIndexed { index, item ->
                        defaultAssignments[item.id] = index % count
                    }
                    _itemAssignments.postValue(defaultAssignments)

                    buildPersonsList(count, finalTotal, MODE_EQUAL, existingGuests, items, defaultAssignments)
                }
            }.onFailure { err ->
                _isLoading.postValue(false)
                _errorMessage.postValue(err.message ?: "Không thể tải thông tin đơn đặt bàn")
            }
        }
    }

    fun setMode(newMode: String) {
        if (_mode.value == newMode) return
        _mode.value = newMode
        recalculateShares()
    }

    fun changeGuestCount(delta: Int) {
        val current = _guestCount.value ?: 2
        val updated = (current + delta).coerceIn(2, 20)
        if (updated == current) return

        _guestCount.value = updated

        // Adjust item assignments if any item was assigned to an index >= updated
        val currentAssignments = _itemAssignments.value.orEmpty().toMutableMap()
        for ((itemId, personIdx) in currentAssignments) {
            if (personIdx >= updated) {
                currentAssignments[itemId] = 0
            }
        }
        _itemAssignments.value = currentAssignments

        recalculateShares()
    }

    fun assignDish(dishId: String, personIndex: Int) {
        val currentAssignments = _itemAssignments.value.orEmpty().toMutableMap()
        currentAssignments[dishId] = personIndex
        _itemAssignments.value = currentAssignments
        recalculateShares()
    }

    private fun recalculateShares() {
        val count = _guestCount.value ?: 2
        val total = _totalPayable.value ?: 0L
        val currentMode = _mode.value ?: MODE_EQUAL
        val items = _orderItems.value.orEmpty()
        val assignments = _itemAssignments.value.orEmpty()
        val existingPersons = _persons.value.orEmpty()

        buildPersonsList(count, total, currentMode, emptyList(), items, assignments, existingPersons)
    }

    private fun buildPersonsList(
        numPersons: Int,
        total: Long,
        mode: String,
        existingGuests: List<BookingGuest> = emptyList(),
        items: List<OrderItem> = _orderItems.value.orEmpty(),
        assignments: Map<String, Int> = _itemAssignments.value.orEmpty(),
        existingUiList: List<SplitPersonUiModel> = emptyList()
    ) {
        var remainderNoticeText: String? = null
        var itemErrorText: String? = null

        val shares: List<Long>
        if (mode == MODE_EQUAL) {
            shares = SplitBillCalculator.calculateEqualSplit(total, numPersons)
            val remainder = if (numPersons > 0) total % numPersons else 0L
            if (remainder > 0L) {
                remainderNoticeText = "Do chia tiền lẻ không tròn, người đầu tiên (chủ bàn) trả thêm $remainder đ tiền làm tròn."
            }
        } else {
            val calcResult = SplitBillCalculator.calculateItemSplit(items, assignments, numPersons, total)
            if (calcResult.isSuccess) {
                shares = calcResult.getOrDefault(emptyList())
            } else {
                shares = SplitBillCalculator.calculateEqualSplit(total, numPersons)
                itemErrorText = calcResult.exceptionOrNull()?.message
            }
        }

        _remainderNotice.postValue(remainderNoticeText)
        _itemAssignError.postValue(itemErrorText)

        // Map assigned dish names per person
        val assignedDishesPerPerson = MutableList(numPersons) { mutableListOf<String>() }
        if (mode == MODE_ITEMS) {
            for (item in items) {
                val pIdx = assignments[item.id]
                if (pIdx != null && pIdx in 0 until numPersons) {
                    assignedDishesPerPerson[pIdx].add("${item.qty}x ${item.name}")
                }
            }
        }

        val updatedPersons = mutableListOf<SplitPersonUiModel>()
        val guestsToSync = mutableListOf<BookingGuest>()

        for (i in 0 until numPersons) {
            val guestId = if (i < existingGuests.size) {
                existingGuests[i].id
            } else if (i < existingUiList.size) {
                existingUiList[i].guestId
            } else {
                "BG_${currentBookingId}_$i"
            }

            val isPaid = if (i < existingGuests.size) {
                existingGuests[i].paid
            } else if (i < existingUiList.size) {
                existingUiList[i].isPaid
            } else {
                false
            }

            val name = if (i == 0) {
                "Người 1 (Chủ bàn)"
            } else {
                "Người ${i + 1}"
            }

            val share = if (i < shares.size) shares[i] else 0L

            updatedPersons.add(
                SplitPersonUiModel(
                    guestId = guestId,
                    name = name,
                    userId = if (i < existingGuests.size) existingGuests[i].userId else "",
                    shareAmount = share,
                    isPaid = isPaid,
                    assignedDishNames = assignedDishesPerPerson[i]
                )
            )

            guestsToSync.add(
                BookingGuest(
                    id = guestId,
                    bookingId = currentBookingId,
                    userId = if (i < existingGuests.size) existingGuests[i].userId else "",
                    status = if (i == 0) "host" else "joined",
                    shareAmount = share,
                    paid = isPaid
                )
            )
        }

        _persons.postValue(updatedPersons)

        val allPaidNow = updatedPersons.isNotEmpty() && updatedPersons.all { it.isPaid }
        _allPaid.postValue(allPaidNow)

        // Đồng bộ danh sách bookingGuests xuống Firestore
        repository.syncGuestsList(currentBookingId, guestsToSync) {}
    }

    /**
     * Xác nhận thanh toán phần chia của một người:
     * - Chống double-tap: khóa isProcessing
     * - Cập nhật repository
     */
    fun payPersonShare(
        person: SplitPersonUiModel,
        method: String,
        onResult: (Boolean, String?) -> Unit
    ) {
        val currentList = _persons.value.orEmpty().toMutableList()
        val index = currentList.indexOfFirst { it.guestId == person.guestId }
        if (index == -1) {
            onResult(false, "Không tìm thấy người cần thanh toán")
            return
        }

        val target = currentList[index]
        if (target.isPaid) {
            onResult(false, "Người này đã thanh toán phần tiền rồi")
            return
        }
        if (target.isProcessing) {
            // Chống double-tap
            return
        }

        // Đánh dấu isProcessing để khóa nút thanh toán
        currentList[index] = target.copy(isProcessing = true)
        _persons.value = currentList

        val allIds = currentList.map { it.guestId }

        repository.payShare(
            bookingId = currentBookingId,
            guestId = target.guestId,
            userId = target.userId,
            amount = target.shareAmount,
            method = method,
            allGuestIds = allIds
        ) { result ->
            result.onSuccess { allCompleted ->
                val refreshedList = _persons.value.orEmpty().toMutableList()
                val targetIdx = refreshedList.indexOfFirst { it.guestId == target.guestId }
                if (targetIdx != -1) {
                    refreshedList[targetIdx] = refreshedList[targetIdx].copy(
                        isPaid = true,
                        isProcessing = false
                    )
                    _persons.postValue(refreshedList)
                }

                _allPaid.postValue(allCompleted)
                onResult(true, null)
            }.onFailure { err ->
                val refreshedList = _persons.value.orEmpty().toMutableList()
                val targetIdx = refreshedList.indexOfFirst { it.guestId == target.guestId }
                if (targetIdx != -1) {
                    refreshedList[targetIdx] = refreshedList[targetIdx].copy(isProcessing = false)
                    _persons.postValue(refreshedList)
                }
                onResult(false, err.message ?: "Lỗi xử lý thanh toán")
            }
        }
    }
}
