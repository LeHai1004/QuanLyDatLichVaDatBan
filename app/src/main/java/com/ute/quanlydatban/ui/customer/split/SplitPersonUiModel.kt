package com.ute.quanlydatban.ui.customer.split

data class SplitPersonUiModel(
    val guestId: String,
    val name: String,
    val userId: String = "",
    val shareAmount: Long = 0L,
    val isPaid: Boolean = false,
    val assignedDishNames: List<String> = emptyList(),
    val isProcessing: Boolean = false
)
