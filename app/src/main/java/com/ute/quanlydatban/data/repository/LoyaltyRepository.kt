package com.ute.quanlydatban.data.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.ute.quanlydatban.data.model.LoyaltyTransaction
import com.ute.quanlydatban.data.model.User
import com.ute.quanlydatban.data.model.UserVoucher
import com.ute.quanlydatban.data.model.Voucher

data class RankProgress(
    val currentPoints: Long,
    val rank: String,
    val nextRank: String,
    val pointsNeeded: Long,
    val progressPercent: Int,
    val isMaxRank: Boolean
)

class LoyaltyRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) : BaseRepository<LoyaltyTransaction>(firestore.collection("loyaltyTransactions"), LoyaltyTransaction::class.java) {

    companion object {
        private const val TAG = "LoyaltyRepository"
        const val POINTS_PER_CURRENCY = 10_000L

        const val RANK_BRONZE = "Đồng"
        const val RANK_SILVER = "Bạc"
        const val RANK_GOLD = "Vàng"

        const val THRESHOLD_SILVER = 100L
        const val THRESHOLD_GOLD = 500L

        fun computeRank(points: Long): String = when {
            points >= THRESHOLD_GOLD -> RANK_GOLD
            points >= THRESHOLD_SILVER -> RANK_SILVER
            else -> RANK_BRONZE
        }

        fun computeNextRankProgress(points: Long): RankProgress {
            val safePoints = points.coerceAtLeast(0L)
            return when {
                safePoints >= THRESHOLD_GOLD -> {
                    RankProgress(
                        currentPoints = safePoints,
                        rank = RANK_GOLD,
                        nextRank = RANK_GOLD,
                        pointsNeeded = 0L,
                        progressPercent = 100,
                        isMaxRank = true
                    )
                }
                safePoints >= THRESHOLD_SILVER -> {
                    val pointsInTier = safePoints - THRESHOLD_SILVER
                    val tierRange = THRESHOLD_GOLD - THRESHOLD_SILVER
                    val percent = ((pointsInTier * 100L) / tierRange).toInt().coerceIn(0, 100)
                    RankProgress(
                        currentPoints = safePoints,
                        rank = RANK_SILVER,
                        nextRank = RANK_GOLD,
                        pointsNeeded = THRESHOLD_GOLD - safePoints,
                        progressPercent = percent,
                        isMaxRank = false
                    )
                }
                else -> {
                    val percent = ((safePoints * 100L) / THRESHOLD_SILVER).toInt().coerceIn(0, 100)
                    RankProgress(
                        currentPoints = safePoints,
                        rank = RANK_BRONZE,
                        nextRank = RANK_SILVER,
                        pointsNeeded = THRESHOLD_SILVER - safePoints,
                        progressPercent = percent,
                        isMaxRank = false
                    )
                }
            }
        }
    }

    fun observeUserProfile(
        userId: String,
        onUpdate: (User) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {
        return firestore.collection("users").document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "observeUserProfile error: ${error.message}", error)
                    onError(error)
                    return@addSnapshotListener
                }
                val user = snapshot?.toObject(User::class.java) ?: User(id = userId)
                onUpdate(user)
            }
    }

    fun observeLoyaltyTransactions(
        userId: String,
        onUpdate: (List<LoyaltyTransaction>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {
        return firestore.collection("loyaltyTransactions")
            .whereEqualTo("userId", userId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "observeLoyaltyTransactions error: ${error.message}", error)
                    onError(error)
                    return@addSnapshotListener
                }
                val list = snapshot?.toObjects(LoyaltyTransaction::class.java).orEmpty()
                onUpdate(list)
            }
    }

    fun getRewardVouchers(onResult: (Result<List<Voucher>>) -> Unit) {
        firestore.collection("vouchers")
            .whereGreaterThan("pointsCost", 0L)
            .get()
            .addOnSuccessListener { snap ->
                val now = System.currentTimeMillis()
                val list = snap.toObjects(Voucher::class.java).filter { v ->
                    v.active && (v.expiry == 0L || v.expiry >= now)
                }
                onResult(Result.success(list))
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "getRewardVouchers error: ${error.message}", error)
                onResult(Result.failure(error))
            }
    }

    fun awardPointsForBooking(
        bookingId: String,
        userId: String,
        paidAmount: Long,
        onResult: (Result<Long>) -> Unit
    ) {
        if (bookingId.isBlank() || userId.isBlank() || paidAmount <= 0L) {
            onResult(Result.success(0L))
            return
        }

        val pointsEarned = paidAmount / POINTS_PER_CURRENCY
        if (pointsEarned <= 0L) {
            onResult(Result.success(0L))
            return
        }

        val txDocId = "TX_EARN_$bookingId"
        val txRef = firestore.collection("loyaltyTransactions").document(txDocId)
        val userRef = firestore.collection("users").document(userId)

        firestore.runTransaction { transaction ->
            val existingTxSnap = transaction.get(txRef)
            if (existingTxSnap.exists()) {
                val existingPoints = existingTxSnap.getLong("points") ?: pointsEarned
                return@runTransaction existingPoints
            }

            val userSnap = transaction.get(userRef)
            val currentPoints = userSnap.getLong("points") ?: 0L
            val newPoints = currentPoints + pointsEarned
            val newRank = computeRank(newPoints)

            transaction.update(userRef, mapOf(
                "points" to newPoints,
                "rank" to newRank
            ))

            val tx = LoyaltyTransaction(
                id = txDocId,
                userId = userId,
                bookingId = bookingId,
                points = pointsEarned,
                reason = "Tích điểm đơn đặt bàn #${bookingId.takeLast(4)}",
                createdAt = System.currentTimeMillis()
            )
            transaction.set(txRef, tx)

            pointsEarned
        }.addOnSuccessListener { points ->
            onResult(Result.success(points))
        }.addOnFailureListener { error ->
            Log.e(TAG, "awardPointsForBooking error: ${error.message}", error)
            onResult(Result.failure(error))
        }
    }

    fun redeemVoucher(
        userId: String,
        voucherId: String,
        onResult: (Result<UserVoucher>) -> Unit
    ) {
        if (userId.isBlank() || voucherId.isBlank()) {
            onResult(Result.failure(IllegalArgumentException("Thông tin không hợp lệ")))
            return
        }

        val userRef = firestore.collection("users").document(userId)
        val voucherRef = firestore.collection("vouchers").document(voucherId)
        val userVoucherRef = firestore.collection("userVouchers").document()
        val txRef = firestore.collection("loyaltyTransactions").document()

        firestore.runTransaction { transaction ->
            val voucherSnap = transaction.get(voucherRef)
            if (!voucherSnap.exists()) {
                throw IllegalStateException("Voucher không tồn tại")
            }
            val voucher = voucherSnap.toObject(Voucher::class.java) ?: throw IllegalStateException("Lỗi dữ liệu voucher")
            val cost = voucher.pointsCost
            if (cost <= 0L) {
                throw IllegalStateException("Voucher này không yêu cầu đổi bằng điểm")
            }
            if (!voucher.active) {
                throw IllegalStateException("Voucher hiện không khả dụng")
            }
            val now = System.currentTimeMillis()
            if (voucher.expiry > 0L && voucher.expiry < now) {
                throw IllegalStateException("Voucher đã hết hạn")
            }

            val userSnap = transaction.get(userRef)
            val currentPoints = userSnap.getLong("points") ?: 0L
            if (currentPoints < cost) {
                throw IllegalStateException("Không đủ điểm để đổi voucher này")
            }

            val newPoints = currentPoints - cost
            val newRank = computeRank(newPoints)

            transaction.update(userRef, mapOf(
                "points" to newPoints,
                "rank" to newRank
            ))

            val userVoucher = UserVoucher(
                id = userVoucherRef.id,
                userId = userId,
                voucherId = voucherId,
                used = false,
                usedAt = 0L
            )
            transaction.set(userVoucherRef, userVoucher)

            val tx = LoyaltyTransaction(
                id = txRef.id,
                userId = userId,
                bookingId = "",
                points = -cost,
                reason = "Đổi ưu đãi ${voucher.code.ifBlank { voucher.id }}",
                createdAt = now
            )
            transaction.set(txRef, tx)

            userVoucher
        }.addOnSuccessListener { uv ->
            onResult(Result.success(uv))
        }.addOnFailureListener { error ->
            Log.e(TAG, "redeemVoucher error: ${error.message}", error)
            onResult(Result.failure(error))
        }
    }

    fun revokePointsForBooking(
        bookingId: String,
        userId: String,
        onResult: (Result<Long>) -> Unit
    ) {
        if (bookingId.isBlank() || userId.isBlank()) {
            onResult(Result.success(0L))
            return
        }

        val earnTxDocId = "TX_EARN_$bookingId"
        val earnTxRef = firestore.collection("loyaltyTransactions").document(earnTxDocId)
        val revokeTxDocId = "TX_REVOKE_$bookingId"
        val revokeTxRef = firestore.collection("loyaltyTransactions").document(revokeTxDocId)
        val userRef = firestore.collection("users").document(userId)

        firestore.runTransaction { transaction ->
            val revokeSnap = transaction.get(revokeTxRef)
            if (revokeSnap.exists()) {
                return@runTransaction 0L
            }

            val earnSnap = transaction.get(earnTxRef)
            if (!earnSnap.exists()) {
                return@runTransaction 0L
            }

            val pointsToRevoke = earnSnap.getLong("points") ?: 0L
            if (pointsToRevoke <= 0L) {
                return@runTransaction 0L
            }

            val userSnap = transaction.get(userRef)
            val currentPoints = userSnap.getLong("points") ?: 0L
            val newPoints = (currentPoints - pointsToRevoke).coerceAtLeast(0L)
            val newRank = computeRank(newPoints)

            transaction.update(userRef, mapOf(
                "points" to newPoints,
                "rank" to newRank
            ))

            val revokeTx = LoyaltyTransaction(
                id = revokeTxDocId,
                userId = userId,
                bookingId = bookingId,
                points = -pointsToRevoke,
                reason = "Hoàn điểm do hủy đơn #${bookingId.takeLast(4)}",
                createdAt = System.currentTimeMillis()
            )
            transaction.set(revokeTxRef, revokeTx)

            pointsToRevoke
        }.addOnSuccessListener { revoked ->
            onResult(Result.success(revoked))
        }.addOnFailureListener { error ->
            Log.e(TAG, "revokePointsForBooking error: ${error.message}", error)
            onResult(Result.failure(error))
        }
    }

    fun deductPointsForDiscount(
        userId: String,
        bookingId: String,
        pointsToUse: Long,
        onResult: (Result<Unit>) -> Unit
    ) {
        if (userId.isBlank() || pointsToUse <= 0L) {
            onResult(Result.success(Unit))
            return
        }

        val txDocId = "TX_DISCOUNT_$bookingId"
        val txRef = firestore.collection("loyaltyTransactions").document(txDocId)
        val userRef = firestore.collection("users").document(userId)

        firestore.runTransaction { transaction ->
            val existingTx = transaction.get(txRef)
            if (existingTx.exists()) {
                return@runTransaction
            }

            val userSnap = transaction.get(userRef)
            val currentPoints = userSnap.getLong("points") ?: 0L
            if (currentPoints < pointsToUse) {
                throw IllegalStateException("Không đủ điểm tích lũy để áp dụng giảm giá")
            }

            val newPoints = currentPoints - pointsToUse
            val newRank = computeRank(newPoints)

            transaction.update(userRef, mapOf(
                "points" to newPoints,
                "rank" to newRank
            ))

            val tx = LoyaltyTransaction(
                id = txDocId,
                userId = userId,
                bookingId = bookingId,
                points = -pointsToUse,
                reason = "Dùng điểm thanh toán đơn #${bookingId.takeLast(4)}",
                createdAt = System.currentTimeMillis()
            )
            transaction.set(txRef, tx)
        }.addOnSuccessListener {
            onResult(Result.success(Unit))
        }.addOnFailureListener { error ->
            Log.e(TAG, "deductPointsForDiscount error: ${error.message}", error)
            onResult(Result.failure(error))
        }
    }
}
