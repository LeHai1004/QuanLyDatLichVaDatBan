package com.ute.quanlydatban.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.ute.quanlydatban.data.model.Voucher
import com.ute.quanlydatban.data.repository.VoucherRepository

class OwnerVoucherViewModel(
    private val voucherRepository: VoucherRepository = VoucherRepository()
) : ViewModel() {

    private val _vouchers = MutableLiveData<List<Voucher>>(emptyList())
    val vouchers: LiveData<List<Voucher>> = _vouchers

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private val _saveSuccess = MutableLiveData<Boolean>(false)
    val saveSuccess: LiveData<Boolean> = _saveSuccess

    fun loadOwnerVouchers(restaurantId: String) {
        _isLoading.value = true
        _errorMessage.value = null
        Log.e("VoucherDebug", "OwnerVoucherViewModel loadOwnerVouchers for restaurantId: $restaurantId")

        voucherRepository.getOwnerVouchers(restaurantId) { result ->
            _isLoading.value = false
            result.onSuccess { list ->
                Log.e("VoucherDebug", "OwnerVouchers loaded successfully, count: ${list.size}")
                _vouchers.value = list
            }.onFailure { err ->
                Log.e("VoucherDebug", "OwnerVouchers load failed: ${err.message}", err)
                _errorMessage.value = "Không thể tải danh sách voucher: ${err.message}"
            }
        }
    }

    fun saveVoucher(voucher: Voucher, restaurantId: String) {
        _isLoading.value = true
        _errorMessage.value = null

        val finalVoucher = voucher.copy(restaurantId = restaurantId)
        voucherRepository.saveVoucher(finalVoucher) { result ->
            _isLoading.value = false
            result.onSuccess {
                _saveSuccess.value = true
                loadOwnerVouchers(restaurantId)
            }.onFailure { err ->
                _errorMessage.value = err.message ?: "Không thể lưu voucher."
            }
        }
    }

    fun toggleActive(voucherId: String, active: Boolean, restaurantId: String, onResult: (Boolean, String?) -> Unit) {
        Log.e("VoucherDebug", "ViewModel toggleActive ID: $voucherId, active: $active")
        voucherRepository.toggleVoucherActive(voucherId, active) { result ->
            result.onSuccess {
                Log.e("VoucherDebug", "ViewModel toggleActive success ID: $voucherId")
                onResult(true, null)
            }.onFailure { err ->
                Log.e("VoucherDebug", "ViewModel toggleActive failure ID: $voucherId, err: ${err.message}", err)
                onResult(false, err.message)
            }
        }
    }

    fun clearMessages() {
        _errorMessage.value = null
        _saveSuccess.value = false
    }

    class Factory(
        private val repo: VoucherRepository = VoucherRepository()
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return OwnerVoucherViewModel(repo) as T
        }
    }
}
