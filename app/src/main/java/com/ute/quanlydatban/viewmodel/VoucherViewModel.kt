package com.ute.quanlydatban.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.firebase.auth.FirebaseAuth
import com.ute.quanlydatban.data.model.UserVoucher
import com.ute.quanlydatban.data.model.Voucher
import com.ute.quanlydatban.data.repository.VoucherRepository

class VoucherViewModel(
    private val voucherRepository: VoucherRepository = VoucherRepository()
) : ViewModel() {

    private val _vouchers = MutableLiveData<List<Pair<Voucher, UserVoucher?>>>(emptyList())
    val vouchers: LiveData<List<Pair<Voucher, UserVoucher?>>> = _vouchers

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private val _appliedVoucher = MutableLiveData<Voucher?>()
    val appliedVoucher: LiveData<Voucher?> = _appliedVoucher

    fun loadVouchers(restaurantId: String = "") {
        val userId = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        _isLoading.value = true
        _errorMessage.value = null

        voucherRepository.getVouchersForCustomer(userId, restaurantId) { result ->
            _isLoading.value = false
            result.onSuccess { list ->
                _vouchers.value = list
            }.onFailure { err ->
                _errorMessage.value = err.message ?: "Không thể tải danh sách voucher."
            }
        }
    }

    fun applyCode(code: String, totalBill: Long = 0L, restaurantId: String = "") {
        val userId = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        _isLoading.value = true
        _errorMessage.value = null

        voucherRepository.applyVoucherCode(userId, code, totalBill, restaurantId) { result ->
            _isLoading.value = false
            result.onSuccess { voucher ->
                _appliedVoucher.value = voucher
            }.onFailure { err ->
                _errorMessage.value = err.message ?: "Mã voucher không hợp lệ."
            }
        }
    }

    fun calculateDiscount(voucher: Voucher, totalBill: Long): Long {
        return voucherRepository.calculateDiscount(voucher, totalBill)
    }

    fun clearMessages() {
        _errorMessage.value = null
        _appliedVoucher.value = null
    }

    class Factory(
        private val repo: VoucherRepository = VoucherRepository()
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return VoucherViewModel(repo) as T
        }
    }
}
