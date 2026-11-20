package com.ute.quanlydatban.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.firebase.firestore.ListenerRegistration
import com.ute.quanlydatban.data.model.WaitlistEntry
import com.ute.quanlydatban.data.repository.WaitlistRepository

class WaitlistViewModel(
    private val repository: WaitlistRepository = WaitlistRepository()
) : ViewModel() {

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> get() = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> get() = _errorMessage

    private val _myWaitlist = MutableLiveData<List<WaitlistEntry>>()
    val myWaitlist: LiveData<List<WaitlistEntry>> get() = _myWaitlist

    private val _ownerWaitlist = MutableLiveData<List<WaitlistEntry>>()
    val ownerWaitlist: LiveData<List<WaitlistEntry>> get() = _ownerWaitlist

    private var myWaitlistRegistration: ListenerRegistration? = null
    private var ownerWaitlistRegistration: ListenerRegistration? = null

    val currentUserId: String?
        get() = repository.currentUserId

    fun checkAndRegister(
        entry: WaitlistEntry,
        onTableAvailable: () -> Unit,
        onRegistered: () -> Unit,
        onError: (String) -> Unit
    ) {
        _isLoading.value = true
        _errorMessage.value = null

        // 1. Kiểm tra xem bàn có sẵn không
        repository.checkTableAvailable(
            restaurantId = entry.restaurantId,
            date = entry.date,
            time = entry.time,
            guests = entry.guests
        ) { availableResult ->
            availableResult.fold(
                onSuccess = { isAvailable ->
                    if (isAvailable) {
                        _isLoading.value = false
                        onTableAvailable()
                    } else {
                        // 2. Không có bàn trống phù hợp -> đăng ký vào waitlist
                        repository.registerWaitlist(entry) { registerResult ->
                            _isLoading.value = false
                            registerResult.fold(
                                onSuccess = {
                                    onRegistered()
                                },
                                onFailure = { err ->
                                    val msg = err.message ?: "Đăng ký danh sách chờ thất bại"
                                    _errorMessage.value = msg
                                    onError(msg)
                                }
                            )
                        }
                    }
                },
                onFailure = { err ->
                    _isLoading.value = false
                    val msg = err.message ?: "Không thể kiểm tra tình trạng bàn"
                    _errorMessage.value = msg
                    onError(msg)
                }
            )
        }
    }

    fun listenMyWaitlist() {
        val uid = currentUserId ?: return
        _isLoading.value = true
        myWaitlistRegistration?.remove()
        myWaitlistRegistration = repository.listenMyWaitlist(uid) { result ->
            _isLoading.value = false
            result.fold(
                onSuccess = { entries ->
                    _myWaitlist.value = entries
                },
                onFailure = { err ->
                    _errorMessage.value = err.message
                }
            )
        }
    }

    fun listenOwnerWaitlist(restaurantId: String, date: String? = null) {
        _isLoading.value = true
        ownerWaitlistRegistration?.remove()
        ownerWaitlistRegistration = repository.listenOwnerWaitlist(restaurantId, date) { result ->
            _isLoading.value = false
            result.fold(
                onSuccess = { entries ->
                    _ownerWaitlist.value = entries
                },
                onFailure = { err ->
                    _errorMessage.value = err.message
                }
            )
        }
    }

    fun markHandled(waitlistId: String, onResult: ((Boolean) -> Unit)? = null) {
        repository.markHandled(waitlistId) { result ->
            result.fold(
                onSuccess = {
                    onResult?.invoke(true)
                },
                onFailure = { err ->
                    _errorMessage.value = err.message
                    onResult?.invoke(false)
                }
            )
        }
    }

    fun notifyNextInWaitlist(
        restaurantId: String,
        date: String,
        time: String,
        tableCapacity: Long,
        onNotified: (WaitlistEntry?) -> Unit
    ) {
        repository.notifyNextInWaitlist(restaurantId, date, time, tableCapacity) { result ->
            result.fold(
                onSuccess = { candidate ->
                    onNotified(candidate)
                },
                onFailure = {
                    onNotified(null)
                }
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        myWaitlistRegistration?.remove()
        ownerWaitlistRegistration?.remove()
    }

    class Factory(private val repo: WaitlistRepository = WaitlistRepository()) :
        ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return WaitlistViewModel(repo) as T
        }
    }
}
