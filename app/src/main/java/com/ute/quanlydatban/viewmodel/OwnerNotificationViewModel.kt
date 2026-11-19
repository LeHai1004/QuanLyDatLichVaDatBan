package com.ute.quanlydatban.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.ute.quanlydatban.data.model.AppNotification
import com.ute.quanlydatban.data.repository.NotificationRepository
import com.ute.quanlydatban.util.toViMessage

class OwnerNotificationViewModel(
    private val repository: NotificationRepository
) : ViewModel() {

    private var rawList: List<AppNotification> = emptyList()

    private val _notifications = MutableLiveData<List<AppNotification>>(emptyList())
    val notifications: LiveData<List<AppNotification>> = _notifications

    private val _selectedFilter = MutableLiveData("all") // "all" | "unread"
    val selectedFilter: LiveData<String> = _selectedFilter

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _isEmpty = MutableLiveData(false)
    val isEmpty: LiveData<Boolean> = _isEmpty

    private val _errorMessage = MutableLiveData<String?>(null)
    val errorMessage: LiveData<String?> = _errorMessage

    private val _actionMessage = MutableLiveData<String?>(null)
    val actionMessage: LiveData<String?> = _actionMessage

    private val _bookingDeletedWarning = MutableLiveData<String?>(null)
    val bookingDeletedWarning: LiveData<String?> = _bookingDeletedWarning

    private val _navigateToBookingId = MutableLiveData<String?>(null)
    val navigateToBookingId: LiveData<String?> = _navigateToBookingId

    init {
        loadNotifications()
    }

    // Tải danh sách thông báo cho chủ quán
    fun loadNotifications() {
        val userId = repository.currentUserId ?: "demo_owner_id"
        _isLoading.value = true
        _errorMessage.value = null

        repository.getNotifications(userId) { result ->
            _isLoading.value = false
            result.fold(
                onSuccess = { list ->
                    rawList = list
                    applyFilter(_selectedFilter.value ?: "all")
                },
                onFailure = { error ->
                    _errorMessage.value = error.toViMessage()
                }
            )
        }
    }

    // Lọc danh sách theo "Tất cả" hoặc "Chưa đọc"
    fun setFilter(filter: String) {
        _selectedFilter.value = filter
        applyFilter(filter)
    }

    private fun applyFilter(filter: String) {
        val filtered = when (filter) {
            "unread" -> rawList.filter { !it.read }
            else -> rawList
        }
        _notifications.value = filtered
        _isEmpty.value = filtered.isEmpty()
    }

    // Xử lý khi bấm vào một thông báo
    fun onNotificationClicked(notification: AppNotification) {
        // Đánh dấu đã đọc trên Firestore nếu chưa đọc
        if (!notification.read) {
            repository.markAsRead(notification.id) { result ->
                result.onSuccess {
                    rawList = rawList.map {
                        if (it.id == notification.id) it.copy(read = true) else it
                    }
                    applyFilter(_selectedFilter.value ?: "all")
                }
            }
        }

        // Kiểm tra xem thông báo có trỏ tới đơn đặt bàn hay không
        if (notification.targetId.isNotEmpty()) {
            repository.checkBookingExists(notification.targetId) { result ->
                result.fold(
                    onSuccess = { exists ->
                        if (exists) {
                            _navigateToBookingId.value = notification.targetId
                        } else {
                            // Thông báo trỏ tới đơn đã bị xóa: báo lỗi tiếng Việt, không để app văng
                            _bookingDeletedWarning.value = "Đơn đặt bàn này không tồn tại hoặc đã bị xóa."
                        }
                    },
                    onFailure = {
                        _bookingDeletedWarning.value = "Không thể kiểm tra thông tin đơn đặt bàn."
                    }
                )
            }
        }
    }

    // Đánh dấu đọc tất cả
    fun markAllAsRead() {
        val userId = repository.currentUserId ?: "demo_owner_id"
        repository.markAllAsRead(userId) { result ->
            result.fold(
                onSuccess = {
                    rawList = rawList.map { it.copy(read = true) }
                    applyFilter(_selectedFilter.value ?: "all")
                    _actionMessage.value = "Đã đánh dấu đọc tất cả thông báo"
                },
                onFailure = { error ->
                    _errorMessage.value = error.toViMessage()
                }
            )
        }
    }

    fun clearNavigationEvent() {
        _navigateToBookingId.value = null
        _bookingDeletedWarning.value = null
        _actionMessage.value = null
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (!modelClass.isAssignableFrom(OwnerNotificationViewModel::class.java)) {
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
                return checkNotNull(modelClass.cast(OwnerNotificationViewModel(NotificationRepository())))
            }
        }
    }
}
