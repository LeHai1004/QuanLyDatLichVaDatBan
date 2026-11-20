package com.ute.quanlydatban.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.ute.quanlydatban.data.model.DiningTable
import com.ute.quanlydatban.data.repository.TableRepository

class OwnerTableManageViewModel(
    private val tableRepo: TableRepository = TableRepository()
) : ViewModel() {

    private val _table = MutableLiveData<DiningTable?>()
    val table: LiveData<DiningTable?> = _table

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private val _nameError = MutableLiveData<String?>()
    val nameError: LiveData<String?> = _nameError

    private val _capacityError = MutableLiveData<String?>()
    val capacityError: LiveData<String?> = _capacityError

    private val _saveSuccess = MutableLiveData<Boolean>(false)
    val saveSuccess: LiveData<Boolean> = _saveSuccess

    private val _deleteSuccess = MutableLiveData<Boolean>(false)
    val deleteSuccess: LiveData<Boolean> = _deleteSuccess

    private var restaurantId: String = ""
    private var tableId: String = ""

    fun init(restaurantId: String, tableId: String?) {
        this.restaurantId = restaurantId
        this.tableId = tableId.orEmpty()

        if (this.tableId.isNotBlank()) {
            _isLoading.value = true
            tableRepo.getTable(restaurantId, this.tableId) { result ->
                _isLoading.value = false
                result.onSuccess { t ->
                    _table.value = t
                }.onFailure { err ->
                    _errorMessage.value = err.message ?: "Không tìm thấy thông tin bàn"
                }
            }
        } else {
            // Tạo mới bàn mặc định
            _table.value = DiningTable(
                floor = 1L,
                type = "table",
                capacity = 4L
            )
        }
    }

    fun saveTable(
        name: String,
        floor: Long,
        type: String,
        capacity: Long,
        mapX: Double,
        mapY: Double
    ) {
        _nameError.value = null
        _capacityError.value = null

        val cleanName = name.trim()
        if (cleanName.isBlank()) {
            _nameError.value = "Tên bàn không được để trống."
            return
        }

        if (capacity <= 0) {
            _capacityError.value = "Sức chứa phải lớn hơn 0."
            return
        }

        val current = _table.value
        val targetId = tableId.ifEmpty { current?.id.orEmpty() }
        val generatedQr = current?.qrCode?.ifBlank { null } ?: "TABLE_${cleanName.replace(" ", "_").uppercase()}_$targetId"

        val newTable = DiningTable(
            id = targetId,
            name = cleanName,
            floor = floor,
            type = type,
            capacity = capacity,
            mapX = mapX,
            mapY = mapY,
            status = current?.status ?: "available",
            qrCode = generatedQr
        )

        _isLoading.value = true
        _errorMessage.value = null

        tableRepo.saveTable(restaurantId, newTable) { result ->
            _isLoading.value = false
            result.onSuccess {
                _saveSuccess.value = true
            }.onFailure { err ->
                if (err.message == "DUPLICATE_NAME") {
                    _nameError.value = "Tên bàn đã tồn tại trong quán. Vui lòng đặt tên khác!"
                } else {
                    _errorMessage.value = err.message ?: "Lỗi lưu bàn. Vui lòng thử lại."
                }
            }
        }
    }

    fun deleteTable() {
        if (restaurantId.isBlank() || tableId.isBlank()) return

        _isLoading.value = true
        _errorMessage.value = null

        tableRepo.deleteTable(restaurantId, tableId) { result ->
            _isLoading.value = false
            result.onSuccess {
                _deleteSuccess.value = true
            }.onFailure { err ->
                if (err.message == "TABLE_HAS_ACTIVE_BOOKING") {
                    _errorMessage.value = "Không thể xóa bàn đang có lịch đặt hoặc đang phục vụ khách."
                } else {
                    _errorMessage.value = err.message ?: "Xóa bàn thất bại. Vui lòng thử lại."
                }
            }
        }
    }
}
