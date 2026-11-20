package com.ute.quanlydatban.viewmodel

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.firestore.ListenerRegistration
import com.ute.quanlydatban.data.model.MenuItem
import com.ute.quanlydatban.data.model.Restaurant
import com.ute.quanlydatban.data.repository.MenuRepository
import com.ute.quanlydatban.data.repository.OwnerRepository

sealed class MenuValidationResult {
    object Valid : MenuValidationResult()
    data class Invalid(
        val nameError: String? = null,
        val priceError: String? = null,
        val categoryError: String? = null
    ) : MenuValidationResult()
}

class OwnerMenuViewModel(
    private val ownerRepo: OwnerRepository = OwnerRepository(),
    private val menuRepo: MenuRepository = MenuRepository()
) : ViewModel() {

    private val _restaurant = MutableLiveData<Restaurant?>()
    val restaurant: LiveData<Restaurant?> = _restaurant

    private val _allMenuItems = MutableLiveData<List<MenuItem>>(emptyList())
    val allMenuItems: LiveData<List<MenuItem>> = _allMenuItems

    private val _categories = MutableLiveData<List<String>>(listOf("Tất cả"))
    val categories: LiveData<List<String>> = _categories

    private val _selectedCategory = MutableLiveData<String>("Tất cả")
    val selectedCategory: LiveData<String> = _selectedCategory

    private val _displayItems = MutableLiveData<List<MenuItem>>(emptyList())
    val displayItems: LiveData<List<MenuItem>> = _displayItems

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private var menuListener: ListenerRegistration? = null

    fun loadData() {
        _isLoading.value = true
        _errorMessage.value = null

        ownerRepo.getOwnerRestaurant { result ->
            result.onSuccess { res ->
                _restaurant.value = res
                listenMenuItems(res.id)
            }.onFailure { error ->
                _isLoading.value = false
                _errorMessage.value = error.message ?: "Không thể lấy thông tin nhà hàng"
            }
        }
    }

    private fun listenMenuItems(restaurantId: String) {
        menuListener?.remove()
        menuListener = menuRepo.observeMenuItems(
            restaurantId = restaurantId,
            onUpdate = { items ->
                _isLoading.value = false
                _allMenuItems.value = items

                val catSet = mutableSetOf("Tất cả")
                items.forEach { item ->
                    if (item.category.isNotBlank()) {
                        catSet.add(item.category.trim())
                    }
                }
                _categories.value = catSet.toList()
                recomputeDisplayItems()
            },
            onError = { error ->
                _isLoading.value = false
                _errorMessage.value = error.message ?: "Lỗi kết nối dữ liệu thực đơn"
            }
        )
    }

    fun selectCategory(category: String) {
        _selectedCategory.value = category
        recomputeDisplayItems()
    }

    private fun recomputeDisplayItems() {
        val currentCategory = _selectedCategory.value ?: "Tất cả"
        val items = _allMenuItems.value.orEmpty()
        _displayItems.value = if (currentCategory == "Tất cả") {
            items
        } else {
            items.filter { it.category.equals(currentCategory, ignoreCase = true) }
        }
    }

    fun toggleAvailability(item: MenuItem, isAvailable: Boolean, onDone: (Boolean) -> Unit) {
        menuRepo.updateAvailability(item.id, isAvailable) { result ->
            onDone(result.isSuccess)
        }
    }

    fun deleteDish(itemId: String, onResult: (Result<Unit>) -> Unit) {
        menuRepo.deleteMenuItem(itemId, onResult)
    }

    fun validateInput(name: String, priceStr: String, category: String): MenuValidationResult {
        var nameErr: String? = null
        var priceErr: String? = null
        var catErr: String? = null

        if (name.trim().isEmpty()) {
            nameErr = "Tên món không được để trống"
        }

        val priceLong = priceStr.trim().toLongOrNull()
        if (priceLong == null || priceLong <= 0L) {
            priceErr = "Giá món phải là số lớn hơn 0"
        }

        if (category.trim().isEmpty()) {
            catErr = "Vui lòng chọn hoặc nhập danh mục"
        }

        return if (nameErr == null && priceErr == null && catErr == null) {
            MenuValidationResult.Valid
        } else {
            MenuValidationResult.Invalid(nameErr, priceErr, catErr)
        }
    }

    fun saveDishWithImage(
        currentDish: MenuItem,
        newName: String,
        newPrice: Long,
        newCategory: String,
        newAvailable: Boolean,
        imageUri: Uri?,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val rId = currentDish.restaurantId.ifBlank { _restaurant.value?.id.orEmpty() }
        if (rId.isBlank()) {
            onError("Chưa xác định được mã nhà hàng")
            return
        }

        if (imageUri != null) {
            menuRepo.uploadDishImage(rId, imageUri) { uploadRes ->
                uploadRes.onSuccess { downloadUrl ->
                    val updated = currentDish.copy(
                        restaurantId = rId,
                        name = newName.trim(),
                        price = newPrice,
                        category = newCategory.trim(),
                        available = newAvailable,
                        imageUrl = downloadUrl
                    )
                    menuRepo.saveMenuItem(updated) { saveRes ->
                        saveRes.onSuccess { onSuccess() }
                            .onFailure { onError(it.message ?: "Lỗi lưu món ăn") }
                    }
                }.onFailure { err ->
                    onError(err.message ?: "Lỗi tải ảnh lên hệ thống")
                }
            }
        } else {
            val updated = currentDish.copy(
                restaurantId = rId,
                name = newName.trim(),
                price = newPrice,
                category = newCategory.trim(),
                available = newAvailable
            )
            menuRepo.saveMenuItem(updated) { saveRes ->
                saveRes.onSuccess { onSuccess() }
                    .onFailure { onError(it.message ?: "Lỗi lưu món ăn") }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        menuListener?.remove()
    }
}
