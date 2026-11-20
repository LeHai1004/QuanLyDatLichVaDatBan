package com.ute.quanlydatban.data.repository

import android.net.Uri
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.storage.FirebaseStorage
import com.ute.quanlydatban.data.model.MenuItem
import java.util.UUID

class MenuRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val storage: FirebaseStorage = FirebaseStorage.getInstance()
) : BaseRepository<MenuItem>(firestore.collection("menuItems"), MenuItem::class.java) {

    companion object {
        private const val TAG = "MenuRepository"
        private const val COLLECTION_MENU_ITEMS = "menuItems"
    }

    fun observeMenuItems(
        restaurantId: String,
        onUpdate: (List<MenuItem>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {
        return firestore.collection(COLLECTION_MENU_ITEMS)
            .whereEqualTo("restaurantId", restaurantId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "observeMenuItems error: ${error.message}", error)
                    onError(error)
                    return@addSnapshotListener
                }
                val items = snapshot?.toObjects(MenuItem::class.java).orEmpty()
                onUpdate(items)
            }
    }

    fun getMenuItem(itemId: String, onResult: (Result<MenuItem?>) -> Unit) {
        if (itemId.isBlank()) {
            onResult(Result.success(null))
            return
        }
        firestore.collection(COLLECTION_MENU_ITEMS).document(itemId).get()
            .addOnSuccessListener { snap ->
                val item = snap.toObject(MenuItem::class.java)
                onResult(Result.success(item))
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "getMenuItem error: ${error.message}", error)
                onResult(Result.failure(error))
            }
    }

    fun saveMenuItem(item: MenuItem, onResult: (Result<String>) -> Unit) {
        val docRef = if (item.id.isBlank()) {
            firestore.collection(COLLECTION_MENU_ITEMS).document()
        } else {
            firestore.collection(COLLECTION_MENU_ITEMS).document(item.id)
        }
        val finalItem = item.copy(id = docRef.id)
        docRef.set(finalItem)
            .addOnSuccessListener {
                onResult(Result.success(docRef.id))
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "saveMenuItem error: ${error.message}", error)
                onResult(Result.failure(error))
            }
    }

    fun updateAvailability(itemId: String, available: Boolean, onResult: (Result<Unit>) -> Unit) {
        if (itemId.isBlank()) {
            onResult(Result.failure(IllegalArgumentException("Mã món không hợp lệ")))
            return
        }
        firestore.collection(COLLECTION_MENU_ITEMS).document(itemId)
            .update("available", available)
            .addOnSuccessListener {
                onResult(Result.success(Unit))
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "updateAvailability error: ${error.message}", error)
                onResult(Result.failure(error))
            }
    }

    fun deleteMenuItem(itemId: String, onResult: (Result<Unit>) -> Unit) {
        if (itemId.isBlank()) {
            onResult(Result.failure(IllegalArgumentException("Mã món không hợp lệ")))
            return
        }
        firestore.collection(COLLECTION_MENU_ITEMS).document(itemId).delete()
            .addOnSuccessListener {
                onResult(Result.success(Unit))
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "deleteMenuItem error: ${error.message}", error)
                onResult(Result.failure(error))
            }
    }

    fun uploadDishImage(
        restaurantId: String,
        imageUri: Uri,
        onResult: (Result<String>) -> Unit
    ) {
        val filename = "${UUID.randomUUID()}.jpg"
        val storageRef = storage.reference.child("menu_items/$restaurantId/$filename")
        storageRef.putFile(imageUri)
            .continueWithTask { task ->
                if (!task.isSuccessful) {
                    task.exception?.let { throw it }
                }
                storageRef.downloadUrl
            }
            .addOnSuccessListener { downloadUri ->
                onResult(Result.success(downloadUri.toString()))
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "uploadDishImage error: ${error.message}", error)
                onResult(Result.failure(error))
            }
    }
}
