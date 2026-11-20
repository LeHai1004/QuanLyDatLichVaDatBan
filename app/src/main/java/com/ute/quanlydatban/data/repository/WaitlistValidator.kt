package com.ute.quanlydatban.data.repository

import com.ute.quanlydatban.data.model.DiningTable
import com.ute.quanlydatban.data.model.WaitlistEntry
import java.text.SimpleDateFormat
import java.util.Locale

object WaitlistValidator {

    private const val DATE_TIME_FORMAT = "dd/MM/yyyy HH:mm"

    fun parseDateTime(dateStr: String, timeStr: String): Long? {
        return try {
            val combined = "${dateStr.trim()} ${timeStr.trim()}"
            val sdf = SimpleDateFormat(DATE_TIME_FORMAT, Locale.getDefault())
            sdf.isLenient = false
            sdf.parse(combined)?.time
        } catch (_: Exception) {
            null
        }
    }

    fun isPastTime(dateStr: String, timeStr: String, currentTimeMillis: Long = System.currentTimeMillis()): Boolean {
        val time = parseDateTime(dateStr, timeStr) ?: return false
        return time < currentTimeMillis
    }

    fun isExpired(dateStr: String, timeStr: String, currentTimeMillis: Long = System.currentTimeMillis()): Boolean {
        return isPastTime(dateStr, timeStr, currentTimeMillis)
    }

    fun hasDuplicateWaiting(
        existingEntries: List<WaitlistEntry>,
        userId: String,
        restaurantId: String,
        date: String,
        time: String
    ): Boolean {
        return existingEntries.any { entry ->
            entry.userId == userId &&
                    entry.restaurantId == restaurantId &&
                    entry.date.trim() == date.trim() &&
                    entry.time.trim() == time.trim() &&
                    entry.status == "waiting"
        }
    }

    fun findSuitableTable(
        tables: List<DiningTable>,
        bookedTableIds: Set<String>,
        guests: Long
    ): DiningTable? {
        return tables.firstOrNull { table ->
            table.id !in bookedTableIds && table.capacity >= guests
        }
    }

    fun findNextCandidate(
        waitlistEntries: List<WaitlistEntry>,
        freedTableCapacity: Long,
        currentTimeMillis: Long = System.currentTimeMillis()
    ): WaitlistEntry? {
        return waitlistEntries
            .filter { entry ->
                entry.status == "waiting" &&
                        entry.guests <= freedTableCapacity &&
                        !isExpired(entry.date, entry.time, currentTimeMillis)
            }
            .minByOrNull { it.createdAt }
    }
}
