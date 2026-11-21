package com.ute.quanlydatban.work

import android.content.Context
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.ute.quanlydatban.BookTableApplication
import com.ute.quanlydatban.data.model.Booking
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

object ReminderScheduler {

    const val TAG = "BookingReminder"
    const val REMINDER_LEAD_TIME_MS = 60 * 60 * 1000L // 1 giờ trước giờ hẹn
    const val REMINDER_LEAD_TIME_DEBUG_MS = 2 * 60 * 1000L // 2 phút trước giờ hẹn
    const val PREF_REMINDER_DEMO = "pref_reminder_demo"
    const val KEY_REMINDER_DEMO_ENABLED = "key_reminder_demo_enabled"
    const val WORK_NAME_PREFIX = "booking_reminder_"

    fun getWorkName(bookingId: String): String = "${WORK_NAME_PREFIX}${bookingId}"

    fun getReminderLeadTimeMs(context: Context): Long {
        val isDebug = (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (!isDebug) return REMINDER_LEAD_TIME_MS
        val prefs = context.getSharedPreferences(PREF_REMINDER_DEMO, Context.MODE_PRIVATE)
        return if (prefs.getBoolean(KEY_REMINDER_DEMO_ENABLED, false)) {
            REMINDER_LEAD_TIME_DEBUG_MS
        } else {
            REMINDER_LEAD_TIME_MS
        }
    }

    fun calculateDelay(
        context: Context = BookTableApplication.appContext,
        dateStr: String,
        timeStr: String,
        nowMillis: Long = System.currentTimeMillis()
    ): Long? {
        if (dateStr.isBlank() || timeStr.isBlank()) return null

        val patterns = listOf(
            "dd/MM/yyyy HH:mm",
            "yyyy-MM-dd HH:mm",
            "d/M/yyyy H:m"
        )

        var targetTimeMillis: Long? = null
        for (pattern in patterns) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.getDefault())
                val parsed = sdf.parse("$dateStr $timeStr")
                if (parsed != null) {
                    targetTimeMillis = parsed.time
                    break
                }
            } catch (_: Exception) {
            }
        }

        if (targetTimeMillis == null) return null

        val timeUntilBooking = targetTimeMillis - nowMillis
        if (timeUntilBooking <= 0L) {
            // Giờ đặt bàn đã trôi qua
            return null
        }

        val leadTime = getReminderLeadTimeMs(context)
        val reminderTargetMillis = targetTimeMillis - leadTime
        return if (nowMillis >= reminderTargetMillis) {
            // Đặt gấp trong vòng chưa tới thời gian nhắc -> nhắc ngay
            0L
        } else {
            // Còn trên thời gian nhắc -> lên lịch nhắc trước đúng hạn
            reminderTargetMillis - nowMillis
        }
    }

    fun scheduleReminder(
        context: Context,
        booking: Booking
    ) {
        if (booking.id.isBlank() || booking.date.isBlank() || booking.time.isBlank()) {
            Log.w(TAG, "scheduleReminder: Đơn thiếu thông tin bắt buộc (id/date/time)")
            return
        }

        val delay = calculateDelay(context, booking.date, booking.time)
        if (delay == null) {
            Log.d(TAG, "Đơn #${booking.id} (${booking.date} ${booking.time}) đã qua giờ, bỏ qua lên lịch nhắc")
            return
        }

        val workName = getWorkName(booking.id)
        val data = workDataOf(
            "bookingId" to booking.id,
            "restaurantId" to booking.restaurantId,
            "bookingDate" to booking.date,
            "bookingTime" to booking.time,
            "guests" to booking.guests,
            "userId" to booking.userId
        )

        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            workName,
            ExistingWorkPolicy.REPLACE,
            request
        )

        Log.d(TAG, "Đã lên lịch nhắc cho đơn #${booking.id} sau $delay ms (workName: $workName)")
    }

    fun scheduleReminder(booking: Booking) {
        try {
            scheduleReminder(BookTableApplication.appContext, booking)
        } catch (e: Throwable) {
            Log.e(TAG, "Không thể lấy context tự động để scheduleReminder: ${e.message}")
        }
    }

    fun cancelReminder(context: Context, bookingId: String) {
        if (bookingId.isBlank()) return
        val workName = getWorkName(bookingId)
        WorkManager.getInstance(context).cancelUniqueWork(workName)
        Log.d(TAG, "Đã hủy lịch nhắc cho đơn #$bookingId (workName: $workName)")
    }

    fun cancelReminder(bookingId: String) {
        try {
            cancelReminder(BookTableApplication.appContext, bookingId)
        } catch (e: Throwable) {
            Log.e(TAG, "Không thể lấy context tự động để cancelReminder: ${e.message}")
        }
    }
}
