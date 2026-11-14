package com.ute.quanlydatban.data.repository

import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.ListenerRegistration

// Lớp nền cho mọi Repository. UI/ViewModel không gọi Firestore trực tiếp,
// chỉ gọi qua các lớp con của lớp này. Muốn đổi nguồn dữ liệu (Firebase hoặc
// dữ liệu mẫu) thì chỉ sửa ở Repository.
abstract class BaseRepository<T : Any>(
    protected val collection: CollectionReference,
    private val type: Class<T>
) {

    fun newId(): String = collection.document().id

    fun get(id: String, onResult: (Result<T?>) -> Unit) {
        collection.document(id).get()
            .addOnSuccessListener { onResult(Result.success(it.toObject(type))) }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    fun getAll(onResult: (Result<List<T>>) -> Unit) {
        collection.get()
            .addOnSuccessListener { onResult(Result.success(it.toObjects(type))) }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    fun getWhere(field: String, value: Any, onResult: (Result<List<T>>) -> Unit) {
        collection.whereEqualTo(field, value).get()
            .addOnSuccessListener { onResult(Result.success(it.toObjects(type))) }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    // Ghi đè cả tài liệu theo id (tạo mới nếu chưa có)
    fun save(id: String, item: T, onResult: (Result<Unit>) -> Unit) {
        collection.document(id).set(item)
            .addOnSuccessListener { onResult(Result.success(Unit)) }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    // Chỉ sửa một số trường, không đụng phần còn lại
    fun update(id: String, fields: Map<String, Any>, onResult: (Result<Unit>) -> Unit) {
        collection.document(id).update(fields)
            .addOnSuccessListener { onResult(Result.success(Unit)) }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    fun delete(id: String, onResult: (Result<Unit>) -> Unit) {
        collection.document(id).delete()
            .addOnSuccessListener { onResult(Result.success(Unit)) }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    // Theo dõi realtime. Nhớ gọi remove() khi màn hình đóng (onDestroyView/onDestroy)
    fun listenWhere(
        field: String,
        value: Any,
        onChange: (Result<List<T>>) -> Unit
    ): ListenerRegistration =
        collection.whereEqualTo(field, value).addSnapshotListener { snap, error ->
            when {
                error != null -> onChange(Result.failure(error))
                snap != null -> onChange(Result.success(snap.toObjects(type)))
            }
        }
}