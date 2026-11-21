package com.ute.quanlydatban.data.repository

import android.util.Log
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.ute.quanlydatban.data.model.UserVoucher
import com.ute.quanlydatban.data.model.Voucher
import com.ute.quanlydatban.data.model.VoucherType
import com.ute.quanlydatban.util.formatMoney

class VoucherRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) : BaseRepository<Voucher>(firestore.collection("vouchers"), Voucher::class.java) {

    companion object {
        private const val TAG = "VoucherRepository"
    }

    private fun parseVoucher(doc: DocumentSnapshot): Voucher {
        val raw = doc.toObject(Voucher::class.java) ?: Voucher()
        val activeVal = doc.getBoolean("active") ?: true
        val resIdVal = doc.getString("restaurantId").orEmpty()
        val maxDiscountVal = doc.getLong("maxDiscount") ?: 0L
        val valueVal = doc.getLong("value") ?: raw.value
        val minOrderVal = doc.getLong("minOrder") ?: raw.minOrder
        val expiryVal = doc.getLong("expiry") ?: raw.expiry
        val codeVal = doc.getString("code").orEmpty().ifEmpty { raw.code }
        val typeVal = doc.getString("type").orEmpty().ifEmpty { raw.type }
        return Voucher(
            id = if (raw.id.isNotBlank()) raw.id else doc.id,
            restaurantId = resIdVal,
            code = codeVal,
            type = typeVal,
            value = valueVal,
            minOrder = minOrderVal,
            maxDiscount = maxDiscountVal,
            expiry = expiryVal,
            active = activeVal,
            pointsCost = doc.getLong("pointsCost") ?: raw.pointsCost
        )
    }

    /**
     * Tính giảm viết thành 1 hàm duy nhất calculateDiscount() trong VoucherRepository:
     * amount trừ thẳng; percent = tổng * value / 100 làm tròn xuống đồng,
     * không vượt maxDiscount (nếu > 0), không vượt tổng hóa đơn.
     */
    fun calculateDiscount(voucher: Voucher, totalBill: Long): Long {
        if (totalBill <= 0L || !voucher.active) return 0L
        val rawDiscount = when (voucher.type) {
            VoucherType.AMOUNT -> voucher.value
            VoucherType.PERCENT -> (totalBill * voucher.value) / 100L
            else -> 0L
        }
        val cappedDiscount = if (voucher.type == VoucherType.PERCENT && voucher.maxDiscount > 0L) {
            rawDiscount.coerceAtMost(voucher.maxDiscount)
        } else {
            rawDiscount
        }
        return cappedDiscount.coerceAtLeast(0L).coerceAtMost(totalBill)
    }

    /**
     * Tải danh sách voucher khả dụng cho khách hàng + trạng thái userVoucher
     */
    fun getVouchersForCustomer(
        userId: String,
        restaurantId: String,
        onResult: (Result<List<Pair<Voucher, UserVoucher?>>>) -> Unit
    ) {
        collection.get()
            .addOnSuccessListener { vSnap ->
                val now = System.currentTimeMillis()
                Log.e("VoucherDebug", "Customer getVouchers total docs: ${vSnap.size()}")
                val allVouchers = vSnap.documents.mapNotNull { doc ->
                    try {
                        parseVoucher(doc)
                    } catch (e: Exception) {
                        Log.e("VoucherDebug", "Customer parse error: ${e.message}", e)
                        null
                    }
                }.filter { v ->
                    v.active && (v.expiry == 0L || v.expiry >= now)
                }
                Log.e("VoucherDebug", "Customer valid active vouchers count: ${allVouchers.size}")

                if (userId.isBlank()) {
                    onResult(Result.success(allVouchers.map { Pair(it, null) }))
                    return@addOnSuccessListener
                }

                firestore.collection("userVouchers")
                    .whereEqualTo("userId", userId)
                    .get()
                    .addOnSuccessListener { uvSnap ->
                        val userVouchersMap = uvSnap.toObjects(UserVoucher::class.java)
                            .associateBy { it.voucherId }

                        val resultList = allVouchers.map { voucher ->
                            Pair(voucher, userVouchersMap[voucher.id])
                        }
                        onResult(Result.success(resultList))
                    }
                    .addOnFailureListener { err ->
                        Log.e("VoucherDebug", "Customer load userVouchers error: ${err.message}", err)
                        onResult(Result.success(allVouchers.map { Pair(it, null) }))
                    }
            }
            .addOnFailureListener { err ->
                Log.e("VoucherDebug", "Lỗi getVouchersForCustomer: ${err.message}", err)
                onResult(Result.failure(err))
            }
    }

    /**
     * Nhập mã voucher: kiểm tra tính hợp lệ
     */
    fun applyVoucherCode(
        userId: String,
        code: String,
        totalBill: Long,
        restaurantId: String,
        onResult: (Result<Voucher>) -> Unit
    ) {
        val cleanCode = code.trim().uppercase()
        if (cleanCode.isBlank()) {
            onResult(Result.failure(IllegalArgumentException("Vui lòng nhập mã ưu đãi.")))
            return
        }

        collection.whereEqualTo("code", cleanCode).get()
            .addOnSuccessListener { snap ->
                val doc = snap.documents.firstOrNull()
                val voucher = if (doc != null) parseVoucher(doc) else null
                if (voucher == null) {
                    onResult(Result.failure(IllegalArgumentException("Mã voucher không hợp lệ hoặc không tồn tại.")))
                    return@addOnSuccessListener
                }

                if (!voucher.active) {
                    onResult(Result.failure(IllegalArgumentException("Voucher tạm thời ngưng áp dụng.")))
                    return@addOnSuccessListener
                }

                if (voucher.restaurantId.isNotBlank() && restaurantId.isNotBlank() && voucher.restaurantId != restaurantId) {
                    onResult(Result.failure(IllegalArgumentException("Mã voucher này không áp dụng cho nhà hàng này.")))
                    return@addOnSuccessListener
                }

                val now = System.currentTimeMillis()
                if (voucher.expiry in 1..<now) {
                    onResult(Result.failure(IllegalArgumentException("Mã voucher này đã hết hạn sử dụng.")))
                    return@addOnSuccessListener
                }

                if (totalBill == 0L) {
                    onResult(Result.failure(IllegalArgumentException("Giỏ hàng trống. Vui lòng chọn món trước khi áp dụng mã.")))
                    return@addOnSuccessListener
                }

                if (totalBill < voucher.minOrder) {
                    onResult(Result.failure(IllegalArgumentException("Đơn hàng chưa đạt giá trị tối thiểu ${formatMoney(voucher.minOrder)}.")))
                    return@addOnSuccessListener
                }

                if (userId.isNotBlank()) {
                    firestore.collection("userVouchers")
                        .whereEqualTo("userId", userId)
                        .whereEqualTo("voucherId", voucher.id)
                        .get()
                        .addOnSuccessListener { uvSnap ->
                            val uv = uvSnap.toObjects(UserVoucher::class.java).firstOrNull()
                            if (uv?.used == true) {
                                onResult(Result.failure(IllegalArgumentException("Mã voucher này đã được sử dụng.")))
                            } else {
                                onResult(Result.success(voucher))
                            }
                        }
                        .addOnFailureListener {
                            onResult(Result.success(voucher))
                        }
                } else {
                    onResult(Result.success(voucher))
                }
            }
            .addOnFailureListener { err ->
                Log.e(TAG, "Lỗi applyVoucherCode: ${err.message}", err)
                onResult(Result.failure(err))
            }
    }

    /**
     * Dùng voucher với Transaction để chặn dùng 2 lần
     */
    fun useVoucherTransaction(
        userId: String,
        voucherId: String,
        bookingId: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        if (voucherId.isBlank() || bookingId.isBlank()) {
            onResult(Result.success(Unit))
            return
        }

        val userVoucherId = "UV_${userId}_${voucherId}"
        val userVoucherRef = firestore.collection("userVouchers").document(userVoucherId)
        val bookingRef = firestore.collection("bookings").document(bookingId)

        firestore.runTransaction { transaction ->
            val uvSnap = transaction.get(userVoucherRef)
            val isUsed = uvSnap.getBoolean("used") ?: false
            if (isUsed) {
                throw IllegalStateException("Voucher đã được sử dụng.")
            }

            val now = System.currentTimeMillis()
            val userVoucherData = UserVoucher(
                id = userVoucherId,
                userId = userId,
                voucherId = voucherId,
                used = true,
                usedAt = now
            )

            transaction.set(userVoucherRef, userVoucherData)
            transaction.update(bookingRef, "voucherId", voucherId)
        }.addOnSuccessListener {
            onResult(Result.success(Unit))
        }.addOnFailureListener { err ->
            Log.e(TAG, "Lỗi useVoucherTransaction: ${err.message}", err)
            onResult(Result.failure(err))
        }
    }

    /**
     * Hủy hoặc hết hạn đơn: trả voucher (used = false)
     */
    fun releaseVoucher(bookingId: String, onResult: (Result<Unit>) -> Unit) {
        if (bookingId.isBlank()) {
            onResult(Result.success(Unit))
            return
        }

        val bookingRef = firestore.collection("bookings").document(bookingId)
        bookingRef.get().addOnSuccessListener { bSnap ->
            val voucherId = bSnap.getString("voucherId").orEmpty()
            val userId = bSnap.getString("userId").orEmpty()

            if (voucherId.isBlank()) {
                onResult(Result.success(Unit))
                return@addOnSuccessListener
            }

            val userVoucherId = "UV_${userId}_${voucherId}"
            val userVoucherRef = firestore.collection("userVouchers").document(userVoucherId)

            firestore.runTransaction { transaction ->
                transaction.update(userVoucherRef, mapOf("used" to false, "usedAt" to 0L))
                transaction.update(bookingRef, "voucherId", "")
            }.addOnSuccessListener {
                onResult(Result.success(Unit))
            }.addOnFailureListener { err ->
                Log.e(TAG, "Lỗi releaseVoucher: ${err.message}", err)
                onResult(Result.failure(err))
            }
        }.addOnFailureListener { err ->
            onResult(Result.failure(err))
        }
    }

    /**
     * Chủ quán: lấy danh sách voucher của nhà hàng
     */
    fun getOwnerVouchers(restaurantId: String, onResult: (Result<List<Voucher>>) -> Unit) {
        collection.get()
            .addOnSuccessListener { snap ->
                Log.e("VoucherDebug", "Firestore total vouchers docs: ${snap.size()}, restaurantId: $restaurantId")
                val vouchers = snap.documents.mapNotNull { doc ->
                    try {
                        parseVoucher(doc)
                    } catch (e: Exception) {
                        Log.e("VoucherDebug", "Parse voucher error: ${e.message}", e)
                        null
                    }
                }.filter { v ->
                    v.restaurantId == restaurantId || v.restaurantId.isBlank()
                }
                Log.e("VoucherDebug", "Filtered owner vouchers count: ${vouchers.size}")
                onResult(Result.success(vouchers))
            }
            .addOnFailureListener { err ->
                Log.e("VoucherDebug", "Lỗi getOwnerVouchers: ${err.message}", err)
                onResult(Result.failure(err))
            }
    }

    /**
     * Chủ quán: thêm hoặc sửa voucher
     */
    fun saveVoucher(voucher: Voucher, onResult: (Result<Unit>) -> Unit) {
        val targetId = if (voucher.id.isNotBlank()) voucher.id else "v_${voucher.code.uppercase().trim()}"
        val finalVoucher = voucher.copy(id = targetId, code = voucher.code.uppercase().trim())
        save(targetId, finalVoucher, onResult)
    }

    /**
     * Chủ quán: bật / tắt trạng thái active
     */
    fun toggleVoucherActive(voucherId: String, active: Boolean, onResult: (Result<Unit>) -> Unit) {
        update(voucherId, mapOf("active" to active), onResult)
    }
}
