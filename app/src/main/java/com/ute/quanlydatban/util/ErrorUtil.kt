package com.ute.quanlydatban.util

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.firestore.FirebaseFirestoreException

// Đổi lỗi kỹ thuật thành câu tiếng Việt để hiện bằng Snackbar
fun Throwable.toViMessage(): String = when {
    this is FirebaseNetworkException -> "Không có kết nối mạng. Vui lòng thử lại."
    this is FirebaseFirestoreException &&
            code == FirebaseFirestoreException.Code.PERMISSION_DENIED ->
        "Bạn không có quyền thực hiện thao tác này."
    this is FirebaseFirestoreException &&
            code == FirebaseFirestoreException.Code.UNAVAILABLE ->
        "Máy chủ đang bận. Vui lòng thử lại sau."
    else -> "Có lỗi xảy ra. Vui lòng thử lại."
}