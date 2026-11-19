package com.ute.quanlydatban.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.ute.quanlydatban.data.model.MenuItem
import com.ute.quanlydatban.data.model.Restaurant
import com.ute.quanlydatban.util.SeedData

// Repository quản lý dữ liệu nhà hàng và thực đơn xem trước từ Firestore
class RestaurantRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) : BaseRepository<Restaurant>(firestore.collection("restaurants"), Restaurant::class.java) {

    fun getRestaurants(onResult: (Result<List<Restaurant>>) -> Unit) {
        getAll { result ->
            result.fold(
                onSuccess = { list ->
                    if (list.isEmpty()) {
                        // Tự động nạp seed data mẫu nếu chưa có dữ liệu trên Firestore
                        SeedData.load(firestore) { success ->
                            if (success) {
                                getAll(onResult)
                            } else {
                                onResult(Result.success(emptyList()))
                            }
                        }
                    } else {
                        onResult(Result.success(list))
                    }
                },
                onFailure = { error ->
                    onResult(Result.failure(error))
                }
            )
        }
    }

    fun getRestaurantById(id: String, onResult: (Result<Restaurant?>) -> Unit) {
        get(id, onResult)
    }

    fun getMenuItems(restaurantId: String, onResult: (Result<List<MenuItem>>) -> Unit) {
        firestore.collection("menuItems")
            .whereEqualTo("restaurantId", restaurantId)
            .get()
            .addOnSuccessListener { snapshot ->
                val items = snapshot.toObjects(MenuItem::class.java)
                onResult(Result.success(items))
            }
            .addOnFailureListener { error ->
                onResult(Result.failure(error))
            }
    }
}
