package com.ute.quanlydatban.data.model

// Máy trạng thái: chỉ các bước hợp lệ mới được chuyển. Chỉ Repository gọi đổi trạng thái.
enum class BookingStatus {
    PENDING, WAITING, CONFIRMED, REJECTED, EXPIRED,
    CHECKED_IN, SERVING, PAID, COMPLETED, CANCELLED, NO_SHOW;

    fun canTransitionTo(next: BookingStatus): Boolean = when (this) {
        PENDING -> next == WAITING || next == CONFIRMED || next == EXPIRED || next == CANCELLED
        WAITING -> next == CONFIRMED || next == REJECTED || next == CANCELLED
        CONFIRMED -> next == CHECKED_IN || next == CANCELLED || next == NO_SHOW
        CHECKED_IN -> next == SERVING
        SERVING -> next == PAID
        PAID -> next == COMPLETED
        else -> false
    }

    companion object {
        fun from(value: String): BookingStatus =
            entries.firstOrNull { it.name == value } ?: PENDING
    }
}

enum class OrderItemStatus {
    WAITING, COOKING, DONE, SERVED;

    fun canTransitionTo(next: OrderItemStatus): Boolean = when (this) {
        WAITING -> next == COOKING
        COOKING -> next == DONE
        DONE -> next == SERVED
        SERVED -> false
    }

    companion object {
        fun from(value: String): OrderItemStatus =
            entries.firstOrNull { it.name == value } ?: WAITING
    }
}