package com.ute.quanlydatban.ui.owner.review

data class ReviewItemUiModel(
    val id: String = "",
    val bookingId: String = "",
    val userId: String = "",
    val userName: String = "",
    val userAvatarUrl: String = "",
    val restaurantId: String = "",
    val rating: Long = 5,
    val comment: String = "",
    val createdAt: Long = 0
)
