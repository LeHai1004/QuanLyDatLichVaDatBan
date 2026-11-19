package com.ute.quanlydatban.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.ute.quanlydatban.data.model.Booking
import com.ute.quanlydatban.data.model.BookingStatus
import com.ute.quanlydatban.data.model.Restaurant
import com.ute.quanlydatban.data.repository.DashboardCounts
import com.ute.quanlydatban.data.repository.OwnerRepository
import com.ute.quanlydatban.data.repository.StatsData
import com.ute.quanlydatban.util.toViMessage
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class OwnerDashboardViewModel(
    private val repository: OwnerRepository
) : ViewModel() {

    // Quyền của chủ quán
    private val _isAuthorized = MutableLiveData<Boolean?>(null)
    val isAuthorized: LiveData<Boolean?> = _isAuthorized

    private val _unauthorizedMessage = MutableLiveData<String?>(null)
    val unauthorizedMessage: LiveData<String?> = _unauthorizedMessage

    // Thông tin quán hiện tại
    private val _restaurant = MutableLiveData<Restaurant?>(null)
    val restaurant: LiveData<Restaurant?> = _restaurant

    private val _isOpenNow = MutableLiveData(true)
    val isOpenNow: LiveData<Boolean> = _isOpenNow

    // Số liệu Dashboard
    private val _dashboardCounts = MutableLiveData(DashboardCounts())
    val dashboardCounts: LiveData<DashboardCounts> = _dashboardCounts

    private val _isDashboardLoading = MutableLiveData(false)
    val isDashboardLoading: LiveData<Boolean> = _isDashboardLoading

    private val _dashboardErrorMessage = MutableLiveData<String?>(null)
    val dashboardErrorMessage: LiveData<String?> = _dashboardErrorMessage

    // Thống kê & Báo cáo
    private var rawStatsData: StatsData = StatsData()

    private val _selectedPeriod = MutableLiveData("today") // "today" | "week" | "all"
    val selectedPeriod: LiveData<String> = _selectedPeriod

    private val _totalRevenue = MutableLiveData<Long>(0L)
    val totalRevenue: LiveData<Long> = _totalRevenue

    private val _totalOrdersCount = MutableLiveData(0)
    val totalOrdersCount: LiveData<Int> = _totalOrdersCount

    private val _completedOrdersCount = MutableLiveData(0)
    val completedOrdersCount: LiveData<Int> = _completedOrdersCount

    private val _cancelledOrdersCount = MutableLiveData(0)
    val cancelledOrdersCount: LiveData<Int> = _cancelledOrdersCount

    private val _filteredBookings = MutableLiveData<List<Booking>>(emptyList())
    val filteredBookings: LiveData<List<Booking>> = _filteredBookings

    private val _isStatsLoading = MutableLiveData(false)
    val isStatsLoading: LiveData<Boolean> = _isStatsLoading

    private val _statsErrorMessage = MutableLiveData<String?>(null)
    val statsErrorMessage: LiveData<String?> = _statsErrorMessage

    // Trạng thái cập nhật thông tin quán
    private val _isProfileSaving = MutableLiveData(false)
    val isProfileSaving: LiveData<Boolean> = _isProfileSaving

    private val _profileSaveSuccess = MutableLiveData(false)
    val profileSaveSuccess: LiveData<Boolean> = _profileSaveSuccess

    private val _profileErrorMessage = MutableLiveData<String?>(null)
    val profileErrorMessage: LiveData<String?> = _profileErrorMessage

    private val _nameError = MutableLiveData<String?>(null)
    val nameError: LiveData<String?> = _nameError

    private val _addressError = MutableLiveData<String?>(null)
    val addressError: LiveData<String?> = _addressError

    private val _timeError = MutableLiveData<String?>(null)
    val timeError: LiveData<String?> = _timeError

    private val _depositError = MutableLiveData<String?>(null)
    val depositError: LiveData<String?> = _depositError

    init {
        checkAuthorizationAndLoad()
    }

    // Kiểm tra quyền chủ quán trước khi tải dữ liệu
    fun checkAuthorizationAndLoad() {
        _isDashboardLoading.value = true
        _dashboardErrorMessage.value = null

        repository.checkOwnerRole { roleResult ->
            roleResult.fold(
                onSuccess = {
                    _isAuthorized.value = true
                    _unauthorizedMessage.value = null
                    loadRestaurantAndData()
                },
                onFailure = { error ->
                    _isDashboardLoading.value = false
                    _isAuthorized.value = false
                    _unauthorizedMessage.value = error.message ?: "Tài khoản không có quyền chủ quán"
                }
            )
        }
    }

    // Tải thông tin quán và các số liệu liên quan
    fun loadRestaurantAndData() {
        _isDashboardLoading.value = true
        _dashboardErrorMessage.value = null

        repository.getOwnerRestaurant { result ->
            result.fold(
                onSuccess = { res ->
                    _restaurant.value = res
                    _isOpenNow.value = isCurrentlyOpen(res.openTime, res.closeTime)
                    loadDashboardCounts(res.id)
                    loadStats(res.id)
                },
                onFailure = { error ->
                    _isDashboardLoading.value = false
                    _dashboardErrorMessage.value = error.toViMessage()
                }
            )
        }
    }

    // Tải số liệu đếm của Dashboard
    private fun loadDashboardCounts(restaurantId: String) {
        repository.getDashboardCounts(restaurantId) { result ->
            _isDashboardLoading.value = false
            result.fold(
                onSuccess = { counts ->
                    _dashboardCounts.value = counts
                },
                onFailure = { error ->
                    _dashboardErrorMessage.value = error.toViMessage()
                }
            )
        }
    }

    // Tải số liệu thống kê
    fun loadStats(restaurantId: String? = _restaurant.value?.id) {
        val targetId = restaurantId ?: _restaurant.value?.id ?: return
        _isStatsLoading.value = true
        _statsErrorMessage.value = null

        repository.getStatsData(targetId) { result ->
            _isStatsLoading.value = false
            result.fold(
                onSuccess = { data ->
                    rawStatsData = data
                    computeStatsForPeriod(_selectedPeriod.value ?: "today")
                },
                onFailure = { error ->
                    _statsErrorMessage.value = error.toViMessage()
                }
            )
        }
    }

    // Thay đổi bộ lọc thời gian: "today" | "week" | "all"
    fun setStatsPeriod(period: String) {
        _selectedPeriod.value = period
        computeStatsForPeriod(period)
    }

    private fun computeStatsForPeriod(period: String) {
        val today1 = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val today2 = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
        val oneWeekAgoMillis = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000

        val filtered = rawStatsData.bookings.filter { b ->
            when (period) {
                "today" -> b.date == today1 || b.date == today2 || (b.createdAt > 0 && isSameDay(b.createdAt))
                "week" -> (b.createdAt > 0 && b.createdAt >= oneWeekAgoMillis) || b.date == today1 || b.date == today2
                else -> true
            }
        }

        _filteredBookings.value = filtered
        _totalOrdersCount.value = filtered.size

        val completed = filtered.count {
            val s = it.status.uppercase()
            s == BookingStatus.COMPLETED.name || s == BookingStatus.PAID.name || s == BookingStatus.SERVING.name
        }
        _completedOrdersCount.value = completed

        val cancelled = filtered.count {
            val s = it.status.uppercase()
            s == BookingStatus.CANCELLED.name || s == BookingStatus.REJECTED.name || s == BookingStatus.NO_SHOW.name
        }
        _cancelledOrdersCount.value = cancelled

        // Tính doanh thu: ưu tiên từ bảng payments, hoặc từ tiền cọc của các đơn thành công
        val bookingIds = filtered.map { it.id }.toSet()
        val matchingPayments = rawStatsData.payments.filter { it.bookingId in bookingIds && (it.status == "completed" || it.status == "paid") }

        val revenueFromPayments = matchingPayments.sumOf { it.amount }
        val revenue = if (revenueFromPayments > 0L) {
            revenueFromPayments
        } else {
            filtered.filter {
                val s = it.status.uppercase()
                s != BookingStatus.CANCELLED.name && s != BookingStatus.REJECTED.name
            }.sumOf { it.depositAmount }
        }

        _totalRevenue.value = revenue
    }

    private fun isSameDay(millis: Long): Boolean {
        val c1 = Calendar.getInstance()
        val c2 = Calendar.getInstance().apply { timeInMillis = millis }
        return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) &&
                c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR)
    }

    // Cập nhật thông tin quán (tên, địa chỉ, giờ mở/đóng cửa, tiền cọc)
    fun updateRestaurantProfile(
        name: String,
        address: String,
        openTime: String,
        closeTime: String,
        depositStr: String,
        category: String
    ) {
        val current = _restaurant.value ?: return

        // Xóa thông báo lỗi cũ
        _nameError.value = null
        _addressError.value = null
        _timeError.value = null
        _depositError.value = null
        _profileErrorMessage.value = null
        _profileSaveSuccess.value = false

        var hasError = false

        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) {
            _nameError.value = "Tên nhà hàng không được để trống"
            hasError = true
        }

        val trimmedAddress = address.trim()
        if (trimmedAddress.isEmpty()) {
            _addressError.value = "Địa chỉ không được để trống"
            hasError = true
        }

        val timeRegex = Regex("^([01]\\d|2[0-3]):[0-5]\\d$")
        val trimmedOpen = openTime.trim()
        val trimmedClose = closeTime.trim()
        if (!timeRegex.matches(trimmedOpen) || !timeRegex.matches(trimmedClose)) {
            _timeError.value = "Giờ mở/đóng cửa không đúng định dạng (HH:mm, 00:00 - 23:59)"
            hasError = true
        }

        val deposit = depositStr.trim().toLongOrNull()
        if (deposit == null || deposit < 0L) {
            _depositError.value = "Tiền cọc không được là số âm"
            hasError = true
        }

        if (hasError) return

        _isProfileSaving.value = true

        val updated = current.copy(
            name = trimmedName,
            address = trimmedAddress,
            openTime = trimmedOpen,
            closeTime = trimmedClose,
            depositAmount = deposit ?: current.depositAmount,
            category = category.trim().ifEmpty { current.category }
        )

        repository.updateRestaurant(updated) { result ->
            _isProfileSaving.value = false
            result.fold(
                onSuccess = {
                    _restaurant.value = updated
                    _isOpenNow.value = isCurrentlyOpen(updated.openTime, updated.closeTime)
                    _profileSaveSuccess.value = true
                },
                onFailure = { error ->
                    _profileErrorMessage.value = error.toViMessage()
                }
            )
        }
    }

    fun isCurrentlyOpen(openTime: String, closeTime: String): Boolean {
        if (openTime.isBlank() || closeTime.isBlank()) return true
        return try {
            val now = Calendar.getInstance()
            val currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

            val openParts = openTime.split(":")
            val closeParts = closeTime.split(":")
            val openMinutes = openParts[0].toInt() * 60 + openParts[1].toInt()
            val closeMinutes = closeParts[0].toInt() * 60 + closeParts[1].toInt()

            if (openMinutes <= closeMinutes) {
                currentMinutes in openMinutes..closeMinutes
            } else {
                currentMinutes >= openMinutes || currentMinutes <= closeMinutes
            }
        } catch (e: Exception) {
            true
        }
    }

    fun signOut() {
        repository.signOut()
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (!modelClass.isAssignableFrom(OwnerDashboardViewModel::class.java)) {
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
                return checkNotNull(modelClass.cast(OwnerDashboardViewModel(OwnerRepository())))
            }
        }
    }
}
