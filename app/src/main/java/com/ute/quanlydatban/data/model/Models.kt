package com.ute.quanlydatban.data.model

// Mọi trường đều có giá trị mặc định: Firestore cần constructor rỗng để đọc dữ liệu.
// Tiền luôn là Long (đồng). Thời gian lưu Long (millis).

data class User(
    val id: String = "", val name: String = "", val email: String = "",
    val phone: String = "", val role: String = "customer",
    val points: Long = 0, val rank: String = "Đồng", val avatarUrl: String = ""
)

data class Restaurant(
    val id: String = "", val ownerId: String = "", val name: String = "",
    val address: String = "", val category: String = "",
    val lat: Double = 0.0, val lng: Double = 0.0,
    val openTime: String = "", val closeTime: String = "",
    val rating: Double = 0.0, val depositAmount: Long = 100_000
)

data class DiningTable(
    val id: String = "", val name: String = "", val floor: Long = 1,
    val type: String = "table", val capacity: Long = 2,
    val mapX: Double = 0.0, val mapY: Double = 0.0,
    val status: String = "available", val qrCode: String = ""
)

data class MenuItem(
    val id: String = "", val restaurantId: String = "", val name: String = "",
    val price: Long = 0, val category: String = "",
    val imageUrl: String = "", val available: Boolean = true
)

data class Booking(
    val id: String = "", val userId: String = "", val restaurantId: String = "",
    val tableId: String = "", val date: String = "", val time: String = "",
    val guests: Long = 1, val depositAmount: Long = 0, val voucherId: String = "",
    val status: String = BookingStatus.PENDING.name, val qrCode: String = "",
    val checkedInAt: Long = 0, val createdAt: Long = 0
)

data class BookingGuest(
    val id: String = "", val bookingId: String = "", val userId: String = "",
    val status: String = "invited", val shareAmount: Long = 0, val paid: Boolean = false
)

data class OrderItem(
    val id: String = "", val bookingId: String = "", val menuItemId: String = "",
    val name: String = "", val qty: Long = 1,
    val unitPrice: Long = 0,            // giá chụp tại lúc gọi, đổi menu không làm sai hóa đơn cũ
    val source: String = "pre",         // pre | table
    val status: String = OrderItemStatus.WAITING.name
)

data class ServiceRequest(
    val id: String = "", val bookingId: String = "", val tableId: String = "",
    val type: String = "", val status: String = "open", val createdAt: Long = 0
)

data class Payment(
    val id: String = "", val bookingId: String = "", val userId: String = "",
    val type: String = "deposit",       // deposit | checkout | split
    val amount: Long = 0, val method: String = "vietqr",
    val status: String = "pending", val paidAt: Long = 0
)

object VoucherType {
    const val AMOUNT = "amount"
    const val PERCENT = "percent"
}

data class Voucher(
    val id: String = "",
    val restaurantId: String = "",       // rỗng = mọi quán
    val code: String = "",
    val type: String = VoucherType.AMOUNT,
    val value: Long = 0,
    val minOrder: Long = 0,
    val maxDiscount: Long = 0,           // 0 = không giới hạn
    val expiry: Long = 0,
    val active: Boolean = true,
    val pointsCost: Long = 0
)

data class UserVoucher(
    val id: String = "",
    val userId: String = "",
    val voucherId: String = "",
    val used: Boolean = false,
    val usedAt: Long = 0
)

data class LoyaltyTransaction(
    val id: String = "", val userId: String = "", val bookingId: String = "",
    val points: Long = 0, val reason: String = "", val createdAt: Long = 0
)

data class Favorite(
    val id: String = "", val userId: String = "", val restaurantId: String = "",
    val createdAt: Long = 0
)

data class WaitlistEntry(
    val id: String = "", val userId: String = "", val restaurantId: String = "",
    val date: String = "", val time: String = "", val guests: Long = 1,
    val status: String = "waiting"
)

data class QrLocation(
    val id: String = "", val restaurantId: String = "", val floor: Long = 1,
    val label: String = "", val x: Double = 0.0, val y: Double = 0.0
)

data class MapNode(
    val id: String = "", val restaurantId: String = "", val floor: Long = 1,
    val x: Double = 0.0, val y: Double = 0.0,
    val neighbors: List<String> = emptyList()
)

data class Review(
    val id: String = "", val bookingId: String = "", val userId: String = "",
    val restaurantId: String = "", val rating: Long = 5, val comment: String = ""
)

data class AppNotification(
    val id: String = "", val userId: String = "", val type: String = "",
    val title: String = "", val body: String = "", val read: Boolean = false,
    val createdAt: Long = 0, val targetId: String = ""
)