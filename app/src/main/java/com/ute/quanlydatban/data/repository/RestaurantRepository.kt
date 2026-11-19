package com.ute.quanlydatban.data.repository

import android.util.Log
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
                    Log.e("RestaurantDetail", "Lỗi getRestaurants: ${error.message}", error)
                    onResult(Result.failure(error))
                }
            )
        }
    }

    fun getRestaurantById(id: String, onResult: (Result<Restaurant?>) -> Unit) {
        if (id.isBlank()) {
            Log.e("RestaurantDetail", "getRestaurantById gọi với id rỗng")
            onResult(Result.success(null))
            return
        }
        firestore.collection("restaurants").document(id).get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    val restaurant = snapshot.toObject(Restaurant::class.java)
                    onResult(Result.success(restaurant))
                } else {
                    Log.e("RestaurantDetail", "Không tìm thấy quán với id=$id trong Firestore")
                    onResult(Result.success(null))
                }
            }
            .addOnFailureListener { error ->
                Log.e("RestaurantDetail", "Lỗi getRestaurantById($id): ${error.message}", error)
                onResult(Result.failure(error))
            }
    }

    fun getMenuItems(restaurantId: String, onResult: (Result<List<MenuItem>>) -> Unit) {
        if (restaurantId.isBlank()) {
            Log.e("RestaurantDetail", "getMenuItems gọi với restaurantId rỗng")
            onResult(Result.success(emptyList()))
            return
        }
        firestore.collection("menuItems")
            .whereEqualTo("restaurantId", restaurantId)
            .get()
            .addOnSuccessListener { snapshot ->
                val items = snapshot.toObjects(MenuItem::class.java)
                onResult(Result.success(items))
            }
            .addOnFailureListener { error ->
                Log.e("RestaurantDetail", "Lỗi getMenuItems($restaurantId): ${error.message}", error)
                onResult(Result.failure(error))
            }
    }
}
