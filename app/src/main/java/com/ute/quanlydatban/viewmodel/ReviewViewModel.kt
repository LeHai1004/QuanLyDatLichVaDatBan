package com.ute.quanlydatban.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.ute.quanlydatban.data.model.Booking
import com.ute.quanlydatban.data.model.BookingStatus
import com.ute.quanlydatban.data.model.Restaurant
import com.ute.quanlydatban.data.model.Review
import com.ute.quanlydatban.data.repository.BookingRepository
import com.ute.quanlydatban.data.repository.ReviewRepository

class ReviewViewModel(
    private val reviewRepository: ReviewRepository = ReviewRepository(),
    private val bookingRepository: BookingRepository = BookingRepository()
) : ViewModel() {

    private val _booking = MutableLiveData<Booking?>()
    val booking: LiveData<Booking?> = _booking

    private val _restaurant = MutableLiveData<Restaurant?>()
    val restaurant: LiveData<Restaurant?> = _restaurant

    private val _rating = MutableLiveData<Int>(0)
    val rating: LiveData<Int> = _rating

    private val _comment = MutableLiveData<String>("")
    val comment: LiveData<String> = _comment

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private val _submitSuccess = MutableLiveData<Boolean>(false)
    val submitSuccess: LiveData<Boolean> = _submitSuccess

    fun setRating(stars: Int) {
        _rating.value = stars
    }

    fun setComment(text: String) {
        _comment.value = text
    }

    fun loadBookingAndRestaurant(bookingId: String, restaurantId: String) {
        if (bookingId.isBlank()) return

        _isLoading.value = true
        _errorMessage.value = null

        bookingRepository.get(bookingId) { result ->
            _isLoading.value = false
            result.onSuccess { b ->
                _booking.value = b
                if (b != null) {
                    val actualResId = if (restaurantId.isNotBlank()) restaurantId else b.restaurantId
                    if (actualResId.isNotBlank()) {
                        bookingRepository.getRestaurant(actualResId) { resResult ->
                            resResult.onSuccess { res ->
                                _restaurant.value = res
                            }
                        }
                    }
                }
            }.onFailure { err ->
                _errorMessage.value = err.message ?: "Không thể tải thông tin đơn đặt bàn"
            }
        }
    }

    fun submitReview() {
        val currentBooking = _booking.value
        val stars = _rating.value ?: 0
        val textComment = _comment.value.orEmpty().trim()

        if (currentBooking == null) {
            _errorMessage.value = "Không tìm thấy thông tin đơn đặt bàn."
            return
        }

        if (stars !in 1..5) {
            _errorMessage.value = "Vui lòng chọn số sao đánh giá (từ 1 đến 5 sao)."
            return
        }

        if (currentBooking.status != BookingStatus.PAID.name) {
            _errorMessage.value = "Chỉ có thể đánh giá đơn đã thanh toán xong."
            return
        }

        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: currentBooking.userId
        val newReview = Review(
            id = "REV_${currentBooking.id}",
            bookingId = currentBooking.id,
            userId = userId,
            restaurantId = currentBooking.restaurantId,
            rating = stars.toLong(),
            comment = textComment,
            createdAt = System.currentTimeMillis()
        )

        _isLoading.value = true
        _errorMessage.value = null

        reviewRepository.createReview(newReview) { result ->
            _isLoading.value = false
            result.onSuccess {
                _submitSuccess.value = true
            }.onFailure { err ->
                when (err.message) {
                    "ALREADY_REVIEWED" -> {
                        _errorMessage.value = "Đơn đặt bàn này đã được đánh giá trước đó."
                    }
                    "ALREADY_COMPLETED" -> {
                        _errorMessage.value = "Đơn đặt bàn này đã hoàn tất đánh giá."
                    }
                    "NOT_PAID" -> {
                        _errorMessage.value = "Chỉ có thể đánh giá sau khi đã thanh toán."
                    }
                    else -> {
                        _errorMessage.value = err.message ?: "Gửi đánh giá thất bại. Vui lòng thử lại."
                    }
                }
            }
        }
    }
}
