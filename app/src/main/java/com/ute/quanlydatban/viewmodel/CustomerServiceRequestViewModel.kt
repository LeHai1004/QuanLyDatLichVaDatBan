package com.ute.quanlydatban.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.firebase.firestore.ListenerRegistration
import com.ute.quanlydatban.data.model.Booking
import com.ute.quanlydatban.data.model.ServiceRequest
import com.ute.quanlydatban.data.repository.ServiceRequestRepository

class CustomerServiceRequestViewModel(
    private val repo: ServiceRequestRepository = ServiceRequestRepository()
) : ViewModel() {

    private val _requests = MutableLiveData<List<ServiceRequest>>(emptyList())
    val requests: LiveData<List<ServiceRequest>> = _requests

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private val _successMessage = MutableLiveData<String?>()
    val successMessage: LiveData<String?> = _successMessage

    private var listenerRegistration: ListenerRegistration? = null

    fun startListening(bookingId: String) {
        if (bookingId.isBlank()) return
        stopListening()
        listenerRegistration = repo.listenCustomerRequests(bookingId) { result ->
            result.onSuccess { list ->
                _requests.value = list
            }.onFailure { err ->
                Log.e("CustomerServiceReqVM", "Lỗi listenCustomerRequests: ${err.message}", err)
            }
        }
    }

    fun sendRequest(booking: Booking, type: String) {
        _isLoading.value = true
        _errorMessage.value = null
        _successMessage.value = null

        repo.sendServiceRequest(booking, type) { result ->
            _isLoading.value = false
            result.onSuccess {
                _successMessage.value = "Đã gửi yêu cầu đến nhân viên."
            }.onFailure { err ->
                Log.e("CustomerServiceReqVM", "Lỗi sendServiceRequest: ${err.message}", err)
                when {
                    err.message?.contains("BILL_REQUESTED") == true -> {
                        _errorMessage.value = "Bạn đã yêu cầu tính tiền, không thể gửi thêm yêu cầu."
                    }
                    err.message?.contains("PENDING_EXISTS") == true -> {
                        _errorMessage.value = "Bạn đã gửi yêu cầu này, vui lòng chờ nhân viên."
                    }
                    err.message?.contains("BOOKING_PAID") == true -> {
                        _errorMessage.value = "Đơn đã thanh toán, không thể gửi yêu cầu."
                    }
                    else -> {
                        _errorMessage.value = "Không thể gửi yêu cầu. Vui lòng thử lại."
                    }
                }
            }
        }
    }

    fun stopListening() {
        listenerRegistration?.remove()
        listenerRegistration = null
    }

    fun clearMessages() {
        _errorMessage.value = null
        _successMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        stopListening()
    }

    class Factory(
        private val repository: ServiceRequestRepository = ServiceRequestRepository()
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return CustomerServiceRequestViewModel(repository) as T
        }
    }
}
