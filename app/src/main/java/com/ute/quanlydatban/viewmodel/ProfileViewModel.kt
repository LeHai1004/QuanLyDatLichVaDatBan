package com.ute.quanlydatban.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.firebase.auth.FirebaseAuth
import com.ute.quanlydatban.data.model.User
import com.ute.quanlydatban.data.repository.UserRepository
import com.ute.quanlydatban.data.repository.UserStats

class ProfileViewModel(
    private val userRepository: UserRepository = UserRepository()
) : ViewModel() {

    private val _user = MutableLiveData<User?>()
    val user: LiveData<User?> = _user

    private val _stats = MutableLiveData<UserStats>(UserStats())
    val stats: LiveData<UserStats> = _stats

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private val _saveSuccess = MutableLiveData<Boolean>(false)
    val saveSuccess: LiveData<Boolean> = _saveSuccess

    fun loadProfile() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        if (userId.isBlank()) {
            _user.value = null
            return
        }

        _isLoading.value = true
        _errorMessage.value = null

        userRepository.getUserProfile(userId) { userResult ->
            userResult.onSuccess { u ->
                _user.value = u
                userRepository.getUserStats(userId) { statsResult ->
                    _isLoading.value = false
                    statsResult.onSuccess { s ->
                        _stats.value = s
                    }
                }
            }.onFailure { err ->
                _isLoading.value = false
                _errorMessage.value = err.message ?: "Không thể tải thông tin hồ sơ."
            }
        }
    }

    fun updateProfile(name: String, phone: String, avatarUrl: String) {
        val cleanName = name.trim()
        val cleanPhone = phone.trim()

        if (cleanName.isBlank()) {
            _errorMessage.value = "Vui lòng nhập họ và tên."
            return
        }

        if (!cleanPhone.matches(Regex("^0\\d{9}$"))) {
            _errorMessage.value = "Số điện thoại không đúng định dạng (10 số, bắt đầu bằng 0)."
            return
        }

        val userId = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        if (userId.isBlank()) {
            _errorMessage.value = "Phiên đăng nhập đã hết hạn."
            return
        }

        _isLoading.value = true
        _errorMessage.value = null

        userRepository.updateUserProfile(userId, cleanName, cleanPhone, avatarUrl) { result ->
            _isLoading.value = false
            result.onSuccess {
                _saveSuccess.value = true
                loadProfile()
            }.onFailure { err ->
                _errorMessage.value = err.message ?: "Cập nhật thất bại. Vui lòng thử lại."
            }
        }
    }

    fun clearMessages() {
        _errorMessage.value = null
        _saveSuccess.value = false
    }

    class Factory(
        private val repo: UserRepository = UserRepository()
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ProfileViewModel(repo) as T
        }
    }
}
