package com.ute.quanlydatban.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.ute.quanlydatban.data.model.MenuItem
import com.ute.quanlydatban.data.model.Restaurant
import com.ute.quanlydatban.data.repository.RestaurantRepository
import com.ute.quanlydatban.util.toViMessage
import java.util.Calendar

class HomeViewModel(
    private val repository: RestaurantRepository
) : ViewModel() {

    // Danh sách quán gốc từ Firestore
    private val _restaurants = MutableLiveData<List<Restaurant>>(emptyList())
    val restaurants: LiveData<List<Restaurant>> = _restaurants

    // Trạng thái màn hình Home
    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _isEmpty = MutableLiveData(false)
    val isEmpty: LiveData<Boolean> = _isEmpty

    private val _errorMessage = MutableLiveData<String?>(null)
    val errorMessage: LiveData<String?> = _errorMessage

    // Bộ lọc và tìm kiếm
    private val _searchQuery = MutableLiveData("")
    val searchQuery: LiveData<String> = _searchQuery

    private val _selectedCategory = MutableLiveData("Tất cả")
    val selectedCategory: LiveData<String> = _selectedCategory

    private val _minRating = MutableLiveData(0.0)
    val minRating: LiveData<Double> = _minRating

    // Kết quả tìm kiếm/lọc
    private val _filteredRestaurants = MutableLiveData<List<Restaurant>>(emptyList())
    val filteredRestaurants: LiveData<List<Restaurant>> = _filteredRestaurants

    private val _isSearchLoading = MutableLiveData(false)
    val isSearchLoading: LiveData<Boolean> = _isSearchLoading

    private val _isSearchEmpty = MutableLiveData(false)
    val isSearchEmpty: LiveData<Boolean> = _isSearchEmpty

    private val _searchErrorMessage = MutableLiveData<String?>(null)
    val searchErrorMessage: LiveData<String?> = _searchErrorMessage

    // Dữ liệu chi tiết quán
    private val _currentRestaurant = MutableLiveData<Restaurant?>(null)
    val currentRestaurant: LiveData<Restaurant?> = _currentRestaurant

    private val _menuItems = MutableLiveData<List<MenuItem>>(emptyList())
    val menuItems: LiveData<List<MenuItem>> = _menuItems

    private val _isDetailLoading = MutableLiveData(false)
    val isDetailLoading: LiveData<Boolean> = _isDetailLoading

    private val _detailErrorMessage = MutableLiveData<String?>(null)
    val detailErrorMessage: LiveData<String?> = _detailErrorMessage

    private val _isOpenNow = MutableLiveData(true)
    val isOpenNow: LiveData<Boolean> = _isOpenNow

    init {
        loadRestaurants()
    }

    // Tải danh sách tất cả nhà hàng
    fun loadRestaurants() {
        _isLoading.value = true
        _errorMessage.value = null
        repository.getRestaurants { result ->
            _isLoading.value = false
            result.fold(
                onSuccess = { list ->
                    _restaurants.value = list
                    _isEmpty.value = list.isEmpty()
                    applySearchAndFilter()
                },
                onFailure = { error ->
                    _errorMessage.value = error.toViMessage()
                    _isEmpty.value = false
                }
            )
        }
    }

    // Cập nhật từ khóa tìm kiếm
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        applySearchAndFilter()
    }

    // Cập nhật danh mục lọc
    fun setSelectedCategory(category: String) {
        _selectedCategory.value = category
        applySearchAndFilter()
    }

    // Cập nhật đánh giá sao tối thiểu
    fun setMinRating(rating: Double) {
        _minRating.value = rating
        applySearchAndFilter()
    }

    // Thực hiện lọc kết hợp theo từ khóa, loại món và đánh giá
    fun applySearchAndFilter() {
        val query = _searchQuery.value.orEmpty().trim().lowercase()
        val category = _selectedCategory.value.orEmpty()
        val ratingThreshold = _minRating.value ?: 0.0

        val list = _restaurants.value.orEmpty()
        val filtered = list.filter { r ->
            val matchName = query.isEmpty() || r.name.lowercase().contains(query)
            val matchCategory = category == "Tất cả" || r.category.equals(category, ignoreCase = true)
            val matchRating = r.rating >= ratingThreshold
            matchName && matchCategory && matchRating
        }

        _filteredRestaurants.value = filtered
        _isSearchEmpty.value = filtered.isEmpty()
    }

    // Tải chi tiết một quán theo id
    fun loadRestaurantDetail(restaurantId: String) {
        _isDetailLoading.value = true
        _detailErrorMessage.value = null

        repository.getRestaurantById(restaurantId) { result ->
            result.fold(
                onSuccess = { restaurant ->
                    _currentRestaurant.value = restaurant
                    if (restaurant != null) {
                        _isOpenNow.value = isCurrentlyOpen(restaurant.openTime, restaurant.closeTime)
                        loadMenuItems(restaurant.id)
                    } else {
                        _isDetailLoading.value = false
                        _detailErrorMessage.value = "Không tìm thấy quán"
                    }
                },
                onFailure = { error ->
                    _isDetailLoading.value = false
                    _detailErrorMessage.value = error.toViMessage()
                }
            )
        }
    }

    // Tải danh sách món ăn xem trước của quán
    fun loadMenuItems(restaurantId: String) {
        repository.getMenuItems(restaurantId) { result ->
            _isDetailLoading.value = false
            result.fold(
                onSuccess = { items ->
                    _menuItems.value = items
                },
                onFailure = { error ->
                    _detailErrorMessage.value = error.toViMessage()
                }
            )
        }
    }

    // Kiểm tra quán có đang trong khung giờ mở cửa không
    fun isCurrentlyOpen(openTime: String, closeTime: String): Boolean {
        if (openTime.isBlank() || closeTime.isBlank()) return true
        return try {
            val now = Calendar.getInstance()
            val currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

            val openParts = openTime.split(":")
            val closeParts = closeTime.split(":")
            val openMinutes = openParts[0].toInt() * 60 + openParts[1].toInt()
            val closeMinutes = closeParts[0].toInt() * 60 + closeParts[1].toInt()

            if (openMinutes <= closeMinutes) {
                currentMinutes in openMinutes..closeMinutes
            } else {
                currentMinutes >= openMinutes || currentMinutes <= closeMinutes
            }
        } catch (e: Exception) {
            true
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (!modelClass.isAssignableFrom(HomeViewModel::class.java)) {
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
                return checkNotNull(modelClass.cast(HomeViewModel(RestaurantRepository())))
            }
        }
    }
}
