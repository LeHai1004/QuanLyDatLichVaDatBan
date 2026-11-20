package com.ute.quanlydatban.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.firebase.firestore.ListenerRegistration
import com.ute.quanlydatban.data.model.Restaurant
import com.ute.quanlydatban.data.model.Review
import com.ute.quanlydatban.data.model.User
import com.ute.quanlydatban.data.repository.OwnerRepository
import com.ute.quanlydatban.data.repository.ReviewRepository
import com.ute.quanlydatban.data.repository.UserRepository
import com.ute.quanlydatban.ui.owner.review.ReviewItemUiModel

class OwnerReviewViewModel(
    private val reviewRepository: ReviewRepository = ReviewRepository(),
    private val ownerRepository: OwnerRepository = OwnerRepository(),
    private val userRepository: UserRepository = UserRepository()
) : ViewModel() {

    private var reviewListener: ListenerRegistration? = null
    private var currentRestaurantId: String = ""

    private val userCache = mutableMapOf<String, User>()
    private var allUiReviews: List<ReviewItemUiModel> = emptyList()

    private val _restaurant = MutableLiveData<Restaurant?>()
    val restaurant: LiveData<Restaurant?> = _restaurant

    private val _displayedReviews = MutableLiveData<List<ReviewItemUiModel>>(emptyList())
    val displayedReviews: LiveData<List<ReviewItemUiModel>> = _displayedReviews

    private val _averageRating = MutableLiveData<Double>(0.0)
    val averageRating: LiveData<Double> = _averageRating

    private val _totalReviewCount = MutableLiveData<Int>(0)
    val totalReviewCount: LiveData<Int> = _totalReviewCount

    private val _starCounts = MutableLiveData<Map<Int, Int>>(emptyMap())
    val starCounts: LiveData<Map<Int, Int>> = _starCounts

    private val _selectedStarFilter = MutableLiveData<Int?>(null)
    val selectedStarFilter: LiveData<Int?> = _selectedStarFilter

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private val _isEmpty = MutableLiveData<Boolean>(false)
    val isEmpty: LiveData<Boolean> = _isEmpty

    private val _isEmptyDueToFilter = MutableLiveData<Boolean>(false)
    val isEmptyDueToFilter: LiveData<Boolean> = _isEmptyDueToFilter

    fun initialize(restaurantId: String? = null) {
        if (!restaurantId.isNullOrBlank()) {
            currentRestaurantId = restaurantId
            listenReviews(restaurantId)
        } else {
            loadRestaurantAndReviews()
        }
    }

    private fun loadRestaurantAndReviews() {
        _isLoading.value = true
        _errorMessage.value = null

        ownerRepository.getOwnerRestaurant { result ->
            result.onSuccess { res ->
                _restaurant.postValue(res)
                currentRestaurantId = res.id
                listenReviews(res.id)
            }.onFailure { err ->
                _isLoading.postValue(false)
                _errorMessage.postValue(err.message ?: "Không thể lấy thông tin nhà hàng")
            }
        }
    }

    private fun listenReviews(restaurantId: String) {
        reviewListener?.remove()
        _isLoading.value = true
        _errorMessage.value = null

        reviewListener = reviewRepository.listenReviewsForRestaurant(restaurantId) { result ->
            _isLoading.postValue(false)
            result.onSuccess { reviews ->
                handleIncomingReviews(reviews)
            }.onFailure { err ->
                _errorMessage.postValue(err.message ?: "Lỗi tải danh sách đánh giá")
            }
        }
    }

    private fun handleIncomingReviews(rawReviews: List<Review>) {
        val total = rawReviews.size
        _totalReviewCount.postValue(total)

        // Tính điểm trung bình (làm tròn 1 chữ số thập phân)
        val avg = ReviewRepository.calculateAverageRating(rawReviews)
        _averageRating.postValue(avg)

        // Đồng bộ rating sang Firestore restaurant nếu có restaurantId
        if (currentRestaurantId.isNotBlank()) {
            reviewRepository.syncRestaurantRating(currentRestaurantId)
        }

        // Tính phân bố số sao
        val counts = mutableMapOf<Int, Int>()
        for (i in 1..5) counts[i] = 0
        rawReviews.forEach { r ->
            val star = r.rating.toInt()
            if (star in 1..5) {
                counts[star] = (counts[star] ?: 0) + 1
            }
        }
        _starCounts.postValue(counts)

        // Tải thông tin người dùng cho từng đánh giá
        fetchUserDetailsAndPopulate(rawReviews)
    }

    private fun fetchUserDetailsAndPopulate(rawReviews: List<Review>) {
        if (rawReviews.isEmpty()) {
            allUiReviews = emptyList()
            applyFilter()
            return
        }

        val missingUserIds = rawReviews.map { it.userId }
            .filter { it.isNotBlank() && !userCache.containsKey(it) }
            .distinct()

        if (missingUserIds.isEmpty()) {
            allUiReviews = rawReviews.map { mapToUiModel(it) }
            applyFilter()
            return
        }

        var pending = missingUserIds.size
        for (userId in missingUserIds) {
            userRepository.getUserProfile(userId) { result ->
                result.onSuccess { user ->
                    if (user != null) {
                        userCache[userId] = user
                    }
                }
                pending--
                if (pending <= 0) {
                    allUiReviews = rawReviews.map { mapToUiModel(it) }
                    applyFilter()
                }
            }
        }
    }

    private fun mapToUiModel(review: Review): ReviewItemUiModel {
        val user = userCache[review.userId]
        return ReviewItemUiModel(
            id = review.id,
            bookingId = review.bookingId,
            userId = review.userId,
            userName = user?.name?.ifBlank { "Khách hàng" } ?: "Khách hàng",
            userAvatarUrl = user?.avatarUrl.orEmpty(),
            restaurantId = review.restaurantId,
            rating = review.rating,
            comment = review.comment,
            createdAt = review.createdAt
        )
    }

    fun setStarFilter(star: Int?) {
        _selectedStarFilter.value = star
        applyFilter()
    }

    private fun applyFilter() {
        val star = _selectedStarFilter.value
        val filtered = if (star == null || star !in 1..5) {
            allUiReviews
        } else {
            allUiReviews.filter { it.rating.toInt() == star }
        }

        _displayedReviews.postValue(filtered)

        val totalAll = allUiReviews.size
        val totalFiltered = filtered.size

        if (totalAll == 0) {
            _isEmpty.postValue(true)
            _isEmptyDueToFilter.postValue(false)
        } else if (totalFiltered == 0) {
            _isEmpty.postValue(true)
            _isEmptyDueToFilter.postValue(true)
        } else {
            _isEmpty.postValue(false)
            _isEmptyDueToFilter.postValue(false)
        }
    }

    fun retry() {
        if (currentRestaurantId.isNotBlank()) {
            listenReviews(currentRestaurantId)
        } else {
            loadRestaurantAndReviews()
        }
    }

    override fun onCleared() {
        super.onCleared()
        reviewListener?.remove()
        reviewListener = null
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return OwnerReviewViewModel() as T
            }
        }
    }
}
