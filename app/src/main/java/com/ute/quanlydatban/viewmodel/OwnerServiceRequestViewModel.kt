package com.ute.quanlydatban.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.ute.quanlydatban.data.model.ServiceRequest
import com.ute.quanlydatban.data.repository.ServiceRequestRepository

data class OwnerServiceRequestDisplay(
    val request: ServiceRequest,
    val tableName: String = "",
    val waitingMinutes: Long = 0
)

class OwnerServiceRequestViewModel(
    private val repo: ServiceRequestRepository = ServiceRequestRepository()
) : ViewModel() {

    private val _requests = MutableLiveData<List<OwnerServiceRequestDisplay>>(emptyList())
    val requests: LiveData<List<OwnerServiceRequestDisplay>> = _requests

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private var listenerRegistration: ListenerRegistration? = null

    fun startListening(restaurantId: String) {
        if (restaurantId.isBlank()) return
        _isLoading.value = true
        _errorMessage.value = null

        stopListening()
        listenerRegistration = repo.listenOwnerRequests(restaurantId) { result ->
            _isLoading.value = false
            result.onSuccess { rawList ->
                enrichRequests(restaurantId, rawList)
            }.onFailure { err ->
                Log.e("OwnerServiceReqVM", "Lỗi listenOwnerRequests: ${err.message}", err)
                _errorMessage.value = "Không thể tải danh sách yêu cầu phục vụ."
            }
        }
    }

    private fun enrichRequests(restaurantId: String, rawList: List<ServiceRequest>) {
        if (rawList.isEmpty()) {
            _requests.value = emptyList()
            return
        }

        val firestore = FirebaseFirestore.getInstance()
        val now = System.currentTimeMillis()

        firestore.collection("restaurants").document(restaurantId)
            .collection("tables").get()
            .addOnSuccessListener { tSnap ->
                val tableMap = tSnap.documents.associate { doc ->
                    doc.id to (doc.getString("name") ?: doc.id)
                }

                val displayList = rawList.map { req ->
                    val tName = tableMap[req.tableId] ?: req.tableId.ifEmpty { "Bàn chung" }
                    val diffMs = (now - req.createdAt).coerceAtLeast(0L)
                    val mins = diffMs / (60 * 1000L)
                    OwnerServiceRequestDisplay(
                        request = req,
                        tableName = tName,
                        waitingMinutes = mins
                    )
                }
                _requests.value = displayList
            }
            .addOnFailureListener {
                val fallbackList = rawList.map { req ->
                    val diffMs = (now - req.createdAt).coerceAtLeast(0L)
                    OwnerServiceRequestDisplay(
                        request = req,
                        tableName = req.tableId.ifEmpty { "Bàn chung" },
                        waitingMinutes = diffMs / (60 * 1000L)
                    )
                }
                _requests.value = fallbackList
            }
    }

    fun markHandled(requestId: String, onResult: (Boolean, String?) -> Unit) {
        repo.markHandled(requestId) { result ->
            result.onSuccess {
                onResult(true, null)
            }.onFailure { err ->
                Log.e("OwnerServiceReqVM", "Lỗi markHandled: ${err.message}", err)
                onResult(false, "Xử lý thất bại. Vui lòng thử lại.")
            }
        }
    }

    fun stopListening() {
        listenerRegistration?.remove()
        listenerRegistration = null
    }

    fun clearError() {
        _errorMessage.value = null
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
            return OwnerServiceRequestViewModel(repository) as T
        }
    }
}
