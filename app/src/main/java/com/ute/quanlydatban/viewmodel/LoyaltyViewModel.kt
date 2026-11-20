package com.ute.quanlydatban.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import com.ute.quanlydatban.data.model.LoyaltyTransaction
import com.ute.quanlydatban.data.model.User
import com.ute.quanlydatban.data.model.UserVoucher
import com.ute.quanlydatban.data.model.Voucher
import com.ute.quanlydatban.data.repository.LoyaltyRepository
import com.ute.quanlydatban.data.repository.RankProgress

class LoyaltyViewModel(
    private val loyaltyRepo: LoyaltyRepository = LoyaltyRepository(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : ViewModel() {

    val currentUserId: String
        get() = auth.currentUser?.uid.orEmpty()

    private val _user = MutableLiveData<User?>()
    val user: LiveData<User?> = _user

    private val _rankProgress = MutableLiveData<RankProgress?>()
    val rankProgress: LiveData<RankProgress?> = _rankProgress

    private val _rewardVouchers = MutableLiveData<List<Voucher>>(emptyList())
    val rewardVouchers: LiveData<List<Voucher>> = _rewardVouchers

    private val _transactions = MutableLiveData<List<LoyaltyTransaction>>(emptyList())
    val transactions: LiveData<List<LoyaltyTransaction>> = _transactions

    private val _selectedTab = MutableLiveData<Int>(0) // 0: Rewards, 1: History
    val selectedTab: LiveData<Int> = _selectedTab

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private val _redeemSuccessEvent = MutableLiveData<UserVoucher?>()
    val redeemSuccessEvent: LiveData<UserVoucher?> = _redeemSuccessEvent

    private var userListener: ListenerRegistration? = null
    private var txListener: ListenerRegistration? = null

    fun loadData() {
        val uid = currentUserId
        if (uid.isBlank()) {
            _errorMessage.value = "Vui lòng đăng nhập để xem thông tin tích điểm."
            return
        }

        _isLoading.value = true
        _errorMessage.value = null

        userListener?.remove()
        userListener = loyaltyRepo.observeUserProfile(
            userId = uid,
            onUpdate = { u ->
                _user.value = u
                _rankProgress.value = LoyaltyRepository.computeNextRankProgress(u.points)
                _isLoading.value = false
            },
            onError = { err ->
                _isLoading.value = false
                _errorMessage.value = err.message ?: "Lỗi tải thông tin điểm"
            }
        )

        txListener?.remove()
        txListener = loyaltyRepo.observeLoyaltyTransactions(
            userId = uid,
            onUpdate = { list ->
                _transactions.value = list
            },
            onError = { _ ->
            }
        )

        loadRewardVouchers()
    }

    fun loadRewardVouchers() {
        loyaltyRepo.getRewardVouchers { result ->
            result.onSuccess { list ->
                _rewardVouchers.value = list
            }.onFailure { err ->
                _errorMessage.value = err.message
            }
        }
    }

    fun selectTab(tabIndex: Int) {
        _selectedTab.value = tabIndex
    }

    fun redeemVoucher(voucher: Voucher, onComplete: (Boolean, String?) -> Unit) {
        val uid = currentUserId
        if (uid.isBlank()) {
            onComplete(false, "Vui lòng đăng nhập để đổi ưu đãi.")
            return
        }

        val userPoints = _user.value?.points ?: 0L
        if (userPoints < voucher.pointsCost) {
            onComplete(false, "Không đủ điểm để đổi voucher này.")
            return
        }

        loyaltyRepo.redeemVoucher(uid, voucher.id) { result ->
            result.onSuccess { uv ->
                _redeemSuccessEvent.value = uv
                onComplete(true, null)
            }.onFailure { err ->
                onComplete(false, err.message ?: "Không thể đổi voucher. Vui lòng thử lại sau.")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        userListener?.remove()
        txListener?.remove()
    }
}
