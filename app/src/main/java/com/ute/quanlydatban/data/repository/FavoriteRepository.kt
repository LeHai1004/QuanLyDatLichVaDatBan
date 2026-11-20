package com.ute.quanlydatban.data.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.ute.quanlydatban.data.model.Booking
import com.ute.quanlydatban.data.model.Favorite
import com.ute.quanlydatban.data.model.MenuItem
import com.ute.quanlydatban.data.model.OrderItem
import com.ute.quanlydatban.data.model.Restaurant

data class RebookResult(
    val isRestaurantValid: Boolean,
    val restaurant: Restaurant? = null,
    val guests: Long = 2L,
    val validOrderItems: List<OrderItem> = emptyList(),
    val droppedItemCount: Int = 0,
    val hasPriceChanges: Boolean = false,
    val statusMessage: String? = null
)

class FavoriteRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) : BaseRepository<Favorite>(firestore.collection("favorites"), Favorite::class.java) {

    companion object {
        private const val TAG = "FavoriteRepository"

        fun createDocId(userId: String, restaurantId: String): String = "${userId}_${restaurantId}"
    }

    fun isFavorite(userId: String, restaurantId: String, onResult: (Boolean) -> Unit) {
        if (userId.isBlank() || restaurantId.isBlank()) {
            onResult(false)
            return
        }
        val docId = createDocId(userId, restaurantId)
        firestore.collection("favorites").document(docId).get()
            .addOnSuccessListener { snap ->
                onResult(snap.exists())
            }
            .addOnFailureListener {
                onResult(false)
            }
    }

    fun toggleFavorite(userId: String, restaurantId: String, onResult: (Result<Boolean>) -> Unit) {
        if (userId.isBlank() || restaurantId.isBlank()) {
            onResult(Result.failure(IllegalArgumentException("Chưa đăng nhập hoặc thiếu thông tin nhà hàng")))
            return
        }

        val docId = createDocId(userId, restaurantId)
        val docRef = firestore.collection("favorites").document(docId)

        firestore.runTransaction { transaction ->
            val snap = transaction.get(docRef)
            if (snap.exists()) {
                transaction.delete(docRef)
                false // Đã bỏ thích
            } else {
                val fav = Favorite(
                    id = docId,
                    userId = userId,
                    restaurantId = restaurantId,
                    createdAt = System.currentTimeMillis()
                )
                transaction.set(docRef, fav)
                true // Đã thích
            }
        }.addOnSuccessListener { isFav ->
            onResult(Result.success(isFav))
        }.addOnFailureListener { error ->
            Log.e(TAG, "toggleFavorite error: ${error.message}", error)
            onResult(Result.failure(error))
        }
    }

    fun getFavoriteRestaurants(userId: String, onResult: (Result<List<Restaurant>>) -> Unit) {
        if (userId.isBlank()) {
            onResult(Result.success(emptyList()))
            return
        }

        firestore.collection("favorites")
            .whereEqualTo("userId", userId)
            .get()
            .addOnSuccessListener { favSnap ->
                val favList = favSnap.toObjects(Favorite::class.java)
                val restaurantIds = favList.map { it.restaurantId }.filter { it.isNotBlank() }.distinct()

                if (restaurantIds.isEmpty()) {
                    onResult(Result.success(emptyList()))
                    return@addOnSuccessListener
                }

                firestore.collection("restaurants").get()
                    .addOnSuccessListener { resSnap ->
                        val allRes = resSnap.toObjects(Restaurant::class.java).associateBy { it.id }
                        val matched = restaurantIds.mapNotNull { id -> allRes[id] }
                        onResult(Result.success(matched))
                    }
                    .addOnFailureListener { error ->
                        Log.e(TAG, "getFavoriteRestaurants (fetch restaurants) error: ${error.message}", error)
                        onResult(Result.failure(error))
                    }
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "getFavoriteRestaurants error: ${error.message}", error)
                onResult(Result.failure(error))
            }
    }

    fun checkRebookEligibility(bookingId: String, onResult: (Result<RebookResult>) -> Unit) {
        if (bookingId.isBlank()) {
            onResult(Result.failure(IllegalArgumentException("Mã đơn không hợp lệ")))
            return
        }

        firestore.collection("bookings").document(bookingId).get()
            .addOnSuccessListener { bSnap ->
                val booking = bSnap.toObject(Booking::class.java)
                if (booking == null) {
                    onResult(Result.failure(IllegalStateException("Không tìm thấy đơn đặt bàn cũ")))
                    return@addOnSuccessListener
                }

                val restaurantId = booking.restaurantId
                if (restaurantId.isBlank()) {
                    onResult(Result.failure(IllegalStateException("Đơn cũ không có thông tin nhà hàng")))
                    return@addOnSuccessListener
                }

                firestore.collection("restaurants").document(restaurantId).get()
                    .addOnSuccessListener { rSnap ->
                        val restaurant = rSnap.toObject(Restaurant::class.java)
                        if (restaurant == null) {
                            onResult(Result.success(RebookResult(
                                isRestaurantValid = false,
                                statusMessage = "Nhà hàng này hiện không còn hoạt động."
                            )))
                            return@addOnSuccessListener
                        }

                        // Lấy các món cũ của đơn
                        firestore.collection("orderItems")
                            .whereEqualTo("bookingId", bookingId)
                            .get()
                            .addOnSuccessListener { oSnap ->
                                val oldOrderItems = oSnap.toObjects(OrderItem::class.java)

                                if (oldOrderItems.isEmpty()) {
                                    onResult(Result.success(RebookResult(
                                        isRestaurantValid = true,
                                        restaurant = restaurant,
                                        guests = booking.guests,
                                        validOrderItems = emptyList()
                                    )))
                                    return@addOnSuccessListener
                                }

                                // Lấy thực đơn hiện tại của quán
                                firestore.collection("menuItems")
                                    .whereEqualTo("restaurantId", restaurantId)
                                    .get()
                                    .addOnSuccessListener { mSnap ->
                                        val currentMenuItems = mSnap.toObjects(MenuItem::class.java).associateBy { it.id }

                                        var droppedCount = 0
                                        var priceChanged = false
                                        val validList = mutableListOf<OrderItem>()

                                        for (oldItem in oldOrderItems) {
                                            val currentDish = currentMenuItems[oldItem.menuItemId]
                                            if (currentDish == null || !currentDish.available) {
                                                droppedCount++
                                            } else {
                                                if (currentDish.price != oldItem.unitPrice) {
                                                    priceChanged = true
                                                }
                                                validList.add(oldItem.copy(
                                                    unitPrice = currentDish.price,
                                                    name = currentDish.name
                                                ))
                                            }
                                        }

                                        onResult(Result.success(RebookResult(
                                            isRestaurantValid = true,
                                            restaurant = restaurant,
                                            guests = booking.guests,
                                            validOrderItems = validList,
                                            droppedItemCount = droppedCount,
                                            hasPriceChanges = priceChanged
                                        )))
                                    }
                                    .addOnFailureListener { err ->
                                        onResult(Result.failure(err))
                                    }
                            }
                            .addOnFailureListener { err ->
                                onResult(Result.failure(err))
                            }
                    }
                    .addOnFailureListener { err ->
                        onResult(Result.failure(err))
                    }
            }
            .addOnFailureListener { err ->
                onResult(Result.failure(err))
            }
    }
}
