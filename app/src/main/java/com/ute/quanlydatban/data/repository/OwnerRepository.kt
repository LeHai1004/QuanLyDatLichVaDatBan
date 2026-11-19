package com.ute.quanlydatban.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.ute.quanlydatban.data.model.Booking
import com.ute.quanlydatban.data.model.BookingStatus
import com.ute.quanlydatban.data.model.Payment
import com.ute.quanlydatban.data.model.Restaurant
import com.ute.quanlydatban.util.SeedData
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DashboardCounts(
    val waitingCount: Long = 0,
    val todayCount: Long = 0,
    val servingCount: Long = 0
)

data class StatsData(
    val bookings: List<Booking> = emptyList(),
    val payments: List<Payment> = emptyList()
)

class OwnerRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) : BaseRepository<Restaurant>(firestore.collection(RESTAURANTS_COLLECTION), Restaurant::class.java) {

    val currentUserId: String?
        get() = auth.currentUser?.uid

    // Kiểm tra quyền của chủ quán: bắt buộc phải có role == "owner"
    fun checkOwnerRole(onResult: (Result<Boolean>) -> Unit) {
        val uid = currentUserId
        if (uid == null) {
            onResult(Result.failure(SecurityException("Chưa đăng nhập tài khoản")))
            return
        }

        firestore.collection(USERS_COLLECTION).document(uid).get()
            .addOnSuccessListener { snapshot ->
                val role = snapshot.getString("role")
                if (role == ROLE_OWNER) {
                    onResult(Result.success(true))
                } else {
                    onResult(Result.failure(SecurityException("Tài khoản không có quyền chủ quán (role owner)")))
                }
            }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    // Lấy thông tin nhà hàng của chủ quán (hỗ trợ tài khoản chủ quán demo nạp từ SeedData)
    fun getOwnerRestaurant(onResult: (Result<Restaurant>) -> Unit) {
        val uid = currentUserId ?: ""

        // Tìm quán theo ownerId trùng với uid hiện tại
        firestore.collection(RESTAURANTS_COLLECTION)
            .whereEqualTo("ownerId", uid)
            .get()
            .addOnSuccessListener { snap ->
                val existing = snap.toObjects(Restaurant::class.java).firstOrNull()
                if (existing != null) {
                    onResult(Result.success(existing))
                } else {
                    // Nếu tài khoản demo chưa gắn quán, lấy quán mẫu đầu tiên và gán cho chủ quán
                    firestore.collection(RESTAURANTS_COLLECTION).get()
                        .addOnSuccessListener { allSnap ->
                            val allRestaurants = allSnap.toObjects(Restaurant::class.java)
                            if (allRestaurants.isNotEmpty()) {
                                val target = allRestaurants.first()
                                if (uid.isNotEmpty() && target.ownerId.isEmpty()) {
                                    firestore.collection(RESTAURANTS_COLLECTION).document(target.id)
                                        .update("ownerId", uid)
                                }
                                onResult(Result.success(target.copy(ownerId = uid)))
                            } else {
                                // Tự động nạp seed data nếu cơ sở dữ liệu trống
                                SeedData.load(firestore) { success ->
                                    if (success) {
                                        getOwnerRestaurant(onResult)
                                    } else {
                                        onResult(Result.failure(NoSuchElementException("Chưa có dữ liệu nhà hàng")))
                                    }
                                }
                            }
                        }
                        .addOnFailureListener { onResult(Result.failure(it)) }
                }
            }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    // Cập nhật thông tin quán
    fun updateRestaurant(
        restaurant: Restaurant,
        onResult: (Result<Unit>) -> Unit
    ) {
        val fields = mapOf<String, Any>(
            "name" to restaurant.name,
            "address" to restaurant.address,
            "openTime" to restaurant.openTime,
            "closeTime" to restaurant.closeTime,
            "depositAmount" to restaurant.depositAmount,
            "category" to restaurant.category
        )
        firestore.collection(RESTAURANTS_COLLECTION).document(restaurant.id)
            .update(fields)
            .addOnSuccessListener { onResult(Result.success(Unit)) }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    // Đếm số liệu thống kê cho Dashboard
    fun getDashboardCounts(restaurantId: String, onResult: (Result<DashboardCounts>) -> Unit) {
        firestore.collection(BOOKINGS_COLLECTION)
            .whereEqualTo("restaurantId", restaurantId)
            .get()
            .addOnSuccessListener { snap ->
                val bookings = snap.toObjects(Booking::class.java)
                val todayStr1 = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                val todayStr2 = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())

                var waiting = 0L
                var today = 0L
                var serving = 0L

                bookings.forEach { b ->
                    val status = b.status.uppercase()
                    if (status == BookingStatus.WAITING.name || status == BookingStatus.PENDING.name) {
                        waiting++
                    }
                    if (b.date == todayStr1 || b.date == todayStr2) {
                        today++
                    }
                    if (status == BookingStatus.SERVING.name || status == BookingStatus.CHECKED_IN.name) {
                        serving++
                    }
                }

                onResult(Result.success(DashboardCounts(waiting, today, serving)))
            }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    // Lấy dữ liệu thống kê đơn và thanh toán
    fun getStatsData(restaurantId: String, onResult: (Result<StatsData>) -> Unit) {
        firestore.collection(BOOKINGS_COLLECTION)
            .whereEqualTo("restaurantId", restaurantId)
            .get()
            .addOnSuccessListener { bookingSnap ->
                val bookings = bookingSnap.toObjects(Booking::class.java)
                firestore.collection(PAYMENTS_COLLECTION).get()
                    .addOnSuccessListener { paymentSnap ->
                        val payments = paymentSnap.toObjects(Payment::class.java)
                        val bookingIds = bookings.map { it.id }.toSet()
                        val restaurantPayments = payments.filter { it.bookingId in bookingIds }
                        onResult(Result.success(StatsData(bookings, restaurantPayments)))
                    }
                    .addOnFailureListener {
                        // Nếu bảng payments lỗi hoặc chưa có, dùng danh sách bookings
                        onResult(Result.success(StatsData(bookings, emptyList())))
                    }
            }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    fun signOut() {
        auth.signOut()
    }

    companion object {
        const val USERS_COLLECTION = "users"
        const val RESTAURANTS_COLLECTION = "restaurants"
        const val BOOKINGS_COLLECTION = "bookings"
        const val PAYMENTS_COLLECTION = "payments"
        const val ROLE_CUSTOMER = "customer"
        const val ROLE_OWNER = "owner"
    }
}
