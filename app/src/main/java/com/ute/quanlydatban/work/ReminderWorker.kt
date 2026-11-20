package com.ute.quanlydatban.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.firestore.FirebaseFirestore
import com.ute.quanlydatban.MainActivity
import com.ute.quanlydatban.R
import com.ute.quanlydatban.data.model.AppNotification
import com.ute.quanlydatban.data.model.BookingStatus
import kotlinx.coroutines.tasks.await

class ReminderWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val CHANNEL_ID = "booking_reminder_channel"
        const val CHANNEL_NAME = "Nhắc lịch đặt bàn"
    }

    override suspend fun doWork(): Result {
        val bookingId = inputData.getString("bookingId").orEmpty()
        if (bookingId.isBlank()) {
            Log.w(ReminderScheduler.TAG, "ReminderWorker: bookingId rỗng, kết thúc")
            return Result.success()
        }

        Log.d(ReminderScheduler.TAG, "ReminderWorker bắt đầu xử lý cho đơn #$bookingId (attempt: $runAttemptCount)")

        return try {
            val firestore = FirebaseFirestore.getInstance()
            val bookingDoc = firestore.collection("bookings").document(bookingId).get().await()

            if (!bookingDoc.exists()) {
                Log.d(ReminderScheduler.TAG, "Đơn #$bookingId không còn tồn tại trên Firestore, hủy nhắc")
                return Result.success()
            }

            val statusStr = bookingDoc.getString("status").orEmpty()
            val status = BookingStatus.from(statusStr)

            // CHỈ gửi nhắc khi đơn đang ở trạng thái CONFIRMED
            if (status != BookingStatus.CONFIRMED) {
                Log.d(ReminderScheduler.TAG, "Đơn #$bookingId trạng thái là $statusStr (không phải CONFIRMED), bỏ qua gửi nhắc")
                return Result.success()
            }

            val userId = bookingDoc.getString("userId").orEmpty().ifBlank { inputData.getString("userId").orEmpty() }
            val restaurantId = bookingDoc.getString("restaurantId").orEmpty().ifBlank { inputData.getString("restaurantId").orEmpty() }
            val bookingDate = bookingDoc.getString("date").orEmpty().ifBlank { inputData.getString("bookingDate").orEmpty() }
            val bookingTime = bookingDoc.getString("time").orEmpty().ifBlank { inputData.getString("bookingTime").orEmpty() }
            val guests = bookingDoc.getLong("guests") ?: inputData.getLong("guests", 2L)

            // Lấy tên nhà hàng
            var restaurantName = "nhà hàng"
            if (restaurantId.isNotBlank()) {
                try {
                    val resDoc = firestore.collection("restaurants").document(restaurantId).get().await()
                    val name = resDoc.getString("name")
                    if (!name.isNullOrBlank()) {
                        restaurantName = name
                    }
                } catch (e: Exception) {
                    Log.w(ReminderScheduler.TAG, "Không lấy được tên nhà hàng: ${e.message}")
                }
            }

            val title = "Sắp đến giờ hẹn đặt bàn!"
            val body = "Bạn có lịch hẹn tại $restaurantName lúc $bookingTime hôm nay ($guests khách). Vui lòng đến đúng giờ nhé!"

            // 1. Lưu thông báo vào collection notifications trên Firestore (ID cố định: "reminder_${bookingId}")
            val notiId = "reminder_$bookingId"
            val appNotification = AppNotification(
                id = notiId,
                userId = userId,
                targetId = bookingId,
                type = "reminder",
                title = title,
                body = body,
                read = false,
                createdAt = System.currentTimeMillis()
            )

            firestore.collection("notifications").document(notiId).set(appNotification).await()
            Log.d(ReminderScheduler.TAG, "Đã lưu thông báo nhắc hẹn Firestore cho đơn #$bookingId")

            // 2. Hiển thị thông báo trên thanh trạng thái hệ thống
            showSystemNotification(bookingId, title, body)

            Result.success()
        } catch (e: Exception) {
            Log.e(ReminderScheduler.TAG, "ReminderWorker gặp lỗi khi thực thi: ${e.message}", e)
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    private fun showSystemNotification(bookingId: String, title: String, body: String) {
        createNotificationChannel()

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_NAVIGATE_TO", "customerNotificationFragment")
            putExtra("EXTRA_BOOKING_ID", bookingId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            bookingId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
            ) {
                NotificationManagerCompat.from(context).notify(bookingId.hashCode(), notification)
                Log.d(ReminderScheduler.TAG, "Đã gửi thông báo hệ thống nhắc lịch hẹn #$bookingId")
            } else {
                Log.w(ReminderScheduler.TAG, "Chưa được cấp quyền POST_NOTIFICATIONS, chỉ lưu thông báo trên Firestore")
            }
        } catch (e: SecurityException) {
            Log.w(ReminderScheduler.TAG, "SecurityException khi hiển thị notification: ${e.message}")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Kênh thông báo nhắc nhở giờ hẹn đặt bàn"
                enableLights(true)
                enableVibration(true)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}
