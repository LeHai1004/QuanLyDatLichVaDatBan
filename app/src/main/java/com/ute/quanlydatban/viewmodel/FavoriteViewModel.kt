package com.ute.quanlydatban.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.ute.quanlydatban.data.model.Restaurant
import com.ute.quanlydatban.data.repository.FavoriteRepository

class FavoriteViewModel(
    private val favoriteRepo: FavoriteRepository = FavoriteRepository(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : ViewModel() {

    val currentUserId: String
        get() = auth.currentUser?.uid.orEmpty()

    private val _favorites = MutableLiveData<List<Restaurant>>(emptyList())
    val favorites: LiveData<List<Restaurant>> = _favorites

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    fun loadFavorites() {
        val uid = currentUserId
        if (uid.isBlank()) {
            _favorites.value = emptyList()
            return
        }

        _isLoading.value = true
        _errorMessage.value = null

        favoriteRepo.getFavoriteRestaurants(uid) { result ->
            _isLoading.value = false
            result.onSuccess { list ->
                _favorites.value = list
            }.onFailure { error ->
                _errorMessage.value = error.message ?: "Không thể tải danh sách yêu thích"
            }
        }
    }

    fun removeFavorite(restaurantId: String, onComplete: (Boolean) -> Unit) {
        val uid = currentUserId
        if (uid.isBlank()) {
            onComplete(false)
            return
        }

        // Optimistic UI update
        val currentList = _favorites.value.orEmpty()
        _favorites.value = currentList.filter { it.id != restaurantId }

        favoriteRepo.toggleFavorite(uid, restaurantId) { result ->
            result.onSuccess { isFav ->
                if (isFav) {
                    // Nếu toggle trả về true (tức là vô tình lại add), reload lại
                    loadFavorites()
                }
                onComplete(true)
            }.onFailure {
                // Rollback lại danh sách cũ
                _favorites.value = currentList
                onComplete(false)
            }
        }
    }
}
