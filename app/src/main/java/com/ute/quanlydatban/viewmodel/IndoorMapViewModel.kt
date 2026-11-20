package com.ute.quanlydatban.viewmodel

import android.graphics.PointF
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.ute.quanlydatban.data.model.Booking
import com.ute.quanlydatban.data.model.BookingStatus
import com.ute.quanlydatban.data.model.DiningTable
import com.ute.quanlydatban.data.model.MapNode
import com.ute.quanlydatban.data.model.QrLocation
import com.ute.quanlydatban.data.model.Restaurant
import com.ute.quanlydatban.data.repository.MapRepository
import com.ute.quanlydatban.util.PathFinder
import com.ute.quanlydatban.util.PathResult

class IndoorMapViewModel(
    private val repository: MapRepository = MapRepository()
) : ViewModel() {

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _booking = MutableLiveData<Booking?>()
    val booking: LiveData<Booking?> = _booking

    private val _restaurant = MutableLiveData<Restaurant?>()
    val restaurant: LiveData<Restaurant?> = _restaurant

    private val _bookedTable = MutableLiveData<DiningTable?>()
    val bookedTable: LiveData<DiningTable?> = _bookedTable

    private val _allTables = MutableLiveData<List<DiningTable>>(emptyList())
    val allTables: LiveData<List<DiningTable>> = _allTables

    private val _mapNodes = MutableLiveData<List<MapNode>>(emptyList())
    val mapNodes: LiveData<List<MapNode>> = _mapNodes

    private val _currentQrLocation = MutableLiveData<QrLocation?>()
    val currentQrLocation: LiveData<QrLocation?> = _currentQrLocation

    private val _selectedFloor = MutableLiveData<Long>(2)
    val selectedFloor: LiveData<Long> = _selectedFloor

    private val _pathResult = MutableLiveData<PathResult?>()
    val pathResult: LiveData<PathResult?> = _pathResult

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private val _isNavigationAllowed = MutableLiveData(false)
    val isNavigationAllowed: LiveData<Boolean> = _isNavigationAllowed

    fun loadData(bookingId: String, restaurantId: String, initialQrCode: String? = null) {
        _isLoading.value = true
        _errorMessage.value = null

        repository.getBooking(bookingId) { bRes ->
            val b = bRes.getOrNull()
            _booking.postValue(b)

            val status = BookingStatus.from(b?.status ?: "")
            val isCheckedIn = (status == BookingStatus.CHECKED_IN)
            _isNavigationAllowed.postValue(isCheckedIn)

            val effectiveResId = if (restaurantId.isNotBlank()) restaurantId else (b?.restaurantId ?: "")

            if (effectiveResId.isNotBlank()) {
                repository.getRestaurant(effectiveResId) { rRes ->
                    _restaurant.postValue(rRes.getOrNull())
                }

                repository.getTables(effectiveResId) { tRes ->
                    val tables = tRes.getOrDefault(emptyList())
                    _allTables.postValue(tables)

                    val targetTableId = b?.tableId.orEmpty()
                    val targetTable = tables.find { it.id == targetTableId || it.name == targetTableId }
                        ?: tables.firstOrNull { it.floor == 2L }
                        ?: tables.firstOrNull()

                    _bookedTable.postValue(targetTable)
                    if (targetTable != null) {
                        _selectedFloor.postValue(targetTable.floor)
                    }

                    // Tải MapNodes và tính toán đường đi
                    repository.getMapNodes(effectiveResId) { nRes ->
                        val nodes = nRes.getOrDefault(emptyList())
                        _mapNodes.postValue(nodes)

                        // Xử lý vị trí ban đầu (từ QR truyền vào hoặc điểm vào mặc định)
                        val qrToFind = initialQrCode ?: "loc_door"
                        repository.findQrLocation(qrToFind, effectiveResId) { locRes ->
                            val loc = locRes.getOrNull()
                            _currentQrLocation.postValue(loc)
                            recalculatePath(nodes, loc, targetTable, _selectedFloor.value ?: 2L)
                            _isLoading.postValue(false)
                        }
                    }
                }
            } else {
                _isLoading.postValue(false)
            }
        }
    }

    fun selectFloor(floor: Long) {
        _selectedFloor.value = floor
        recalculatePath(
            _mapNodes.value.orEmpty(),
            _currentQrLocation.value,
            _bookedTable.value,
            floor
        )
    }

    fun updateLocationByQr(qrRaw: String) {
        val resId = _restaurant.value?.id.orEmpty()
        _isLoading.value = true
        repository.findQrLocation(qrRaw, resId) { res ->
            _isLoading.postValue(false)
            val loc = res.getOrNull()
            if (loc != null) {
                if (resId.isNotBlank() && loc.restaurantId.isNotBlank() && loc.restaurantId != resId) {
                    _errorMessage.postValue("Mã QR vị trí không thuộc nhà hàng đã đặt bàn")
                    return@findQrLocation
                }
                _currentQrLocation.postValue(loc)
                _selectedFloor.postValue(loc.floor)
                recalculatePath(_mapNodes.value.orEmpty(), loc, _bookedTable.value, loc.floor)
            } else {
                _errorMessage.postValue("Không tìm thấy mã QR vị trí này trong hệ thống")
            }
        }
    }

    private fun recalculatePath(
        nodes: List<MapNode>,
        userLoc: QrLocation?,
        targetTable: DiningTable?,
        displayFloor: Long
    ) {
        if (userLoc == null || targetTable == null || nodes.isEmpty()) {
            _pathResult.postValue(null)
            return
        }

        val result = PathFinder.findPath(
            nodes = nodes,
            startX = userLoc.x,
            startY = userLoc.y,
            startFloor = userLoc.floor,
            targetX = targetTable.mapX,
            targetY = targetTable.mapY,
            targetFloor = targetTable.floor,
            displayFloor = displayFloor
        )

        _pathResult.postValue(result)
        if (result == null) {
            _errorMessage.postValue("Không tìm được đường đi tới bàn")
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    class Factory(
        private val repository: MapRepository = MapRepository()
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(IndoorMapViewModel::class.java)) {
                return IndoorMapViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
