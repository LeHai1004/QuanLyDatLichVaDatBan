package com.ute.quanlydatban.viewmodel

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.firebase.firestore.ListenerRegistration
import com.ute.quanlydatban.R
import com.ute.quanlydatban.data.model.AppNotification
import com.ute.quanlydatban.data.repository.NotificationRepository
import com.ute.quanlydatban.util.toViMessage

class NotificationViewModel(
    private val context: Context,
    private val repository: NotificationRepository
) : ViewModel() {

    private val _notifications = MutableLiveData<List<AppNotification>>(emptyList())
    val notifications: LiveData<List<AppNotification>> = _notifications

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private val _actionMessage = MutableLiveData<ActionMessage?>()
    val actionMessage: LiveData<ActionMessage?> = _actionMessage

    private val _navigationRequest = MutableLiveData<NavigationRequest?>()
    val navigationRequest: LiveData<NavigationRequest?> = _navigationRequest

    private var notificationsListener: ListenerRegistration? = null

    init {
        startListening()
    }

    fun startListening() {
        val userId = repository.currentUserId
        if (userId.isNullOrBlank()) {
            _isLoading.value = false
            _notifications.value = emptyList()
            _errorMessage.value = context.getString(R.string.customer_notification_login_required)
            return
        }

        _isLoading.value = true
        _errorMessage.value = null

        notificationsListener?.remove()
        notificationsListener = repository.observeNotifications(
            userId = userId,
            onResult = { list ->
                _isLoading.value = false
                _notifications.value = list
            },
            onError = { error ->
                _isLoading.value = false
                _errorMessage.value = error.toViMessage()
            }
        )
    }

    fun stopListening() {
        notificationsListener?.remove()
        notificationsListener = null
    }

    fun loadNotifications() {
        startListening()
    }

    override fun onCleared() {
        super.onCleared()
        stopListening()
    }

    fun markAsRead(notification: AppNotification) {
        if (notification.read) {
            handleNotification(notification)
            return
        }
        if (notification.id.isBlank()) {
            _actionMessage.value = ActionMessage(
                context.getString(R.string.customer_notification_missing_id)
            )
            return
        }

        repository.markAsRead(notification.id) { result ->
            result.fold(
                onSuccess = {
                    _notifications.value =
                        _notifications.value.orEmpty().map {
                            if (it.id == notification.id) it.copy(read = true) else it
                        }
                    handleNotification(notification.copy(read = true))
                },
                onFailure = { error ->
                    _actionMessage.postValue(
                        ActionMessage(error.toViMessage()) { markAsRead(notification) }
                    )
                }
            )
        }
    }

    fun markAllAsRead() {
        val userId = repository.currentUserId
        if (userId.isNullOrBlank()) {
            _actionMessage.value = ActionMessage(
                context.getString(R.string.customer_notification_login_required_update)
            )
            return
        }

        repository.markAllAsRead(userId) { result ->
            result.fold(
                onSuccess = {
                    _notifications.value = _notifications.value.orEmpty().map { it.copy(read = true) }
                    _actionMessage.value = ActionMessage(
                        context.getString(R.string.customer_notification_all_read_success)
                    )
                },
                onFailure = { error ->
                    _actionMessage.postValue(
                        ActionMessage(error.toViMessage()) { markAllAsRead() }
                    )
                }
            )
        }
    }

    private fun handleNotification(notification: AppNotification) {
        when (notification.type.lowercase()) {
            TYPE_BOOKING_CONFIRMED,
            TYPE_BOOKING_REJECTED,
            TYPE_QR_TICKET,
            TYPE_BOOKING_QR -> checkBookingAndNavigate(notification)
            TYPE_REMINDER,
            TYPE_BOOKING_REMINDER -> _actionMessage.value =
                ActionMessage(context.getString(R.string.customer_notification_reminder_unavailable))
            TYPE_TABLE_AVAILABLE -> _actionMessage.value =
                ActionMessage(context.getString(R.string.customer_notification_table_unavailable))
            else -> _actionMessage.value =
                ActionMessage(context.getString(R.string.customer_notification_detail_unavailable))
        }
    }

    private fun checkBookingAndNavigate(notification: AppNotification) {
        if (notification.targetId.isBlank()) {
            _actionMessage.value = ActionMessage(
                context.getString(R.string.customer_notification_missing_booking_id)
            )
            return
        }

        repository.checkBookingExists(notification.targetId) { result ->
            result.fold(
                onSuccess = { exists ->
                    if (exists) {
                        val destination = if (notification.type.lowercase() == TYPE_BOOKING_REJECTED) {
                            NavigationDestination.BOOKING_HISTORY
                        } else {
                            NavigationDestination.QR_TICKET
                        }
                        _navigationRequest.value =
                            NavigationRequest(destination, notification.targetId)
                    } else {
                        _actionMessage.value = ActionMessage(
                            context.getString(R.string.customer_notification_booking_missing)
                        )
                    }
                },
                onFailure = { error ->
                    _actionMessage.value = ActionMessage(error.toViMessage()) {
                        checkBookingAndNavigate(notification)
                    }
                }
            )
        }
    }

    fun clearActionMessage() {
        _actionMessage.value = null
    }

    fun clearNavigationRequest() {
        _navigationRequest.value = null
    }

    data class NavigationRequest(
        val destination: NavigationDestination,
        val bookingId: String
    )

    data class ActionMessage(
        val text: String,
        val retry: (() -> Unit)? = null
    )

    enum class NavigationDestination {
        BOOKING_HISTORY,
        QR_TICKET
    }

    companion object {
        private const val TYPE_BOOKING_CONFIRMED = "booking_confirmed"
        private const val TYPE_BOOKING_REJECTED = "booking_rejected"
        private const val TYPE_QR_TICKET = "qr_ticket"
        private const val TYPE_BOOKING_QR = "booking_qr"
        private const val TYPE_REMINDER = "reminder"
        private const val TYPE_BOOKING_REMINDER = "booking_reminder"
        private const val TYPE_TABLE_AVAILABLE = "table_available"

        fun factory(context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (!modelClass.isAssignableFrom(NotificationViewModel::class.java)) {
                        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                    }
                    return checkNotNull(
                        modelClass.cast(
                            NotificationViewModel(
                                context.applicationContext,
                                NotificationRepository()
                            )
                        )
                    )
                }
            }
    }
}
