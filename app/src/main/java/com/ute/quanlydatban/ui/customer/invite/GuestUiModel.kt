package com.ute.quanlydatban.ui.customer.invite

data class GuestUiModel(
    val id: String,
    val userId: String,
    val name: String,
    val avatarUrl: String = "",
    val status: String,
    val isHost: Boolean = false,
    val orderedDishesText: String = ""
)
