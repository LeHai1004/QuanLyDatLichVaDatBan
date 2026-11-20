package com.ute.quanlydatban

import com.ute.quanlydatban.data.model.LoyaltyTransaction
import com.ute.quanlydatban.data.model.User
import com.ute.quanlydatban.data.model.Voucher
import com.ute.quanlydatban.data.repository.LoyaltyRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class LoyaltyUnitTest {

    @Test
    fun testPointsCalculationWithLongType() {
        val ratio = LoyaltyRepository.POINTS_PER_CURRENCY

        val amount1 = 50_000L
        val points1 = amount1 / ratio
        assertEquals(5L, points1)

        val amount2 = 99_999L
        val points2 = amount2 / ratio
        assertEquals(9L, points2)

        val amount3 = 9_000L
        val points3 = amount3 / ratio
        assertEquals(0L, points3)

        val amount4 = 1_250_000L
        val points4 = amount4 / ratio
        assertEquals(125L, points4)
    }

    @Test
    fun testRankCalculationAndProgress() {
        // Hạng Đồng
        assertEquals(LoyaltyRepository.RANK_BRONZE, LoyaltyRepository.computeRank(0L))
        assertEquals(LoyaltyRepository.RANK_BRONZE, LoyaltyRepository.computeRank(99L))

        val progressBronze = LoyaltyRepository.computeNextRankProgress(45L)
        assertEquals(LoyaltyRepository.RANK_BRONZE, progressBronze.rank)
        assertEquals(LoyaltyRepository.RANK_SILVER, progressBronze.nextRank)
        assertEquals(55L, progressBronze.pointsNeeded)
        assertEquals(45, progressBronze.progressPercent)
        assertFalse(progressBronze.isMaxRank)

        // Hạng Bạc
        assertEquals(LoyaltyRepository.RANK_SILVER, LoyaltyRepository.computeRank(100L))
        assertEquals(LoyaltyRepository.RANK_SILVER, LoyaltyRepository.computeRank(499L))

        val progressSilver = LoyaltyRepository.computeNextRankProgress(250L)
        assertEquals(LoyaltyRepository.RANK_SILVER, progressSilver.rank)
        assertEquals(LoyaltyRepository.RANK_GOLD, progressSilver.nextRank)
        assertEquals(250L, progressSilver.pointsNeeded)
        assertEquals(37, progressSilver.progressPercent)
        assertFalse(progressSilver.isMaxRank)

        // Hạng Vàng (Cao nhất)
        assertEquals(LoyaltyRepository.RANK_GOLD, LoyaltyRepository.computeRank(500L))
        assertEquals(LoyaltyRepository.RANK_GOLD, LoyaltyRepository.computeRank(1200L))

        val progressGold = LoyaltyRepository.computeNextRankProgress(750L)
        assertEquals(LoyaltyRepository.RANK_GOLD, progressGold.rank)
        assertEquals(0L, progressGold.pointsNeeded)
        assertEquals(100, progressGold.progressPercent)
        assertTrue(progressGold.isMaxRank)
    }

    @Test
    fun testPreventDuplicatePointsAward() {
        val existingTxIds = mutableSetOf<String>()
        var user = User(id = "U1", points = 100L, rank = LoyaltyRepository.RANK_SILVER)

        fun awardPoints(bookingId: String, paidAmount: Long): Boolean {
            val txId = "TX_EARN_$bookingId"
            if (existingTxIds.contains(txId)) {
                return false // Chặn cộng trùng
            }
            val pointsEarned = paidAmount / LoyaltyRepository.POINTS_PER_CURRENCY
            if (pointsEarned <= 0L) return false

            existingTxIds.add(txId)
            val newPoints = user.points + pointsEarned
            user = user.copy(
                points = newPoints,
                rank = LoyaltyRepository.computeRank(newPoints)
            )
            return true
        }

        // Lần đầu cộng điểm cho booking_1001: thành công
        val firstAward = awardPoints("booking_1001", 300_000L)
        assertTrue(firstAward)
        assertEquals(130L, user.points)

        // Lần thứ 2 cộng điểm cho cùng booking_1001: bị chặn, điểm không đổi
        val secondAward = awardPoints("booking_1001", 300_000L)
        assertFalse(secondAward)
        assertEquals(130L, user.points)
    }

    @Test
    fun testPreventVoucherRedemptionWhenNotEnoughPoints() {
        var user = User(id = "U1", points = 80L, rank = LoyaltyRepository.RANK_BRONZE)
        val voucher = Voucher(id = "V1", code = "GIAM50K", pointsCost = 100L)

        fun redeem(v: Voucher): Result<User> {
            if (user.points < v.pointsCost) {
                return Result.failure(IllegalStateException("Không đủ điểm để đổi voucher này"))
            }
            val newPoints = user.points - v.pointsCost
            user = user.copy(
                points = newPoints,
                rank = LoyaltyRepository.computeRank(newPoints)
            )
            return Result.success(user)
        }

        // Khi điểm không đủ: thất bại
        val failResult = redeem(voucher)
        assertTrue(failResult.isFailure)
        assertEquals("Không đủ điểm để đổi voucher này", failResult.exceptionOrNull()?.message)
        assertEquals(80L, user.points)

        // Khi nạp thêm điểm lên 150: đổi thành công
        user = user.copy(points = 150L, rank = LoyaltyRepository.RANK_SILVER)
        val successResult = redeem(voucher)
        assertTrue(successResult.isSuccess)
        assertEquals(50L, user.points)
        assertEquals(LoyaltyRepository.RANK_BRONZE, user.rank)
    }

    @Test
    fun testLoyaltyTransactionsSumMatchesUserPoints() {
        val transactions = listOf(
            LoyaltyTransaction(id = "1", points = 50L, reason = "Tích điểm đơn 1"),
            LoyaltyTransaction(id = "2", points = 120L, reason = "Tích điểm đơn 2"),
            LoyaltyTransaction(id = "3", points = -100L, reason = "Đổi voucher"),
            LoyaltyTransaction(id = "4", points = 30L, reason = "Tích điểm đơn 3"),
            LoyaltyTransaction(id = "5", points = -50L, reason = "Hoàn điểm đơn 1 hủy")
        )

        val totalCalculated = transactions.sumOf { it.points }
        val user = User(id = "U1", points = totalCalculated, rank = LoyaltyRepository.computeRank(totalCalculated))

        assertEquals(50L, user.points)
        assertEquals(totalCalculated, user.points)
        assertEquals(LoyaltyRepository.RANK_BRONZE, user.rank)
    }

    @Test
    fun testRevokePointsOnCancelledBooking() {
        val transactions = mutableMapOf<String, LoyaltyTransaction>()
        var user = User(id = "U1", points = 150L, rank = LoyaltyRepository.RANK_SILVER)

        // Giả sử trước đó đã có giao dịch cộng 50 điểm cho đơn B2001
        transactions["TX_EARN_B2001"] = LoyaltyTransaction(
            id = "TX_EARN_B2001",
            bookingId = "B2001",
            points = 50L
        )

        fun revokePoints(bookingId: String): Boolean {
            val revokeTxId = "TX_REVOKE_$bookingId"
            if (transactions.containsKey(revokeTxId)) {
                return false // Đã hoàn rồi
            }
            val earnTx = transactions["TX_EARN_$bookingId"] ?: return false
            val pointsToRevoke = earnTx.points

            transactions[revokeTxId] = LoyaltyTransaction(
                id = revokeTxId,
                bookingId = bookingId,
                points = -pointsToRevoke
            )
            val newPoints = (user.points - pointsToRevoke).coerceAtLeast(0L)
            user = user.copy(points = newPoints, rank = LoyaltyRepository.computeRank(newPoints))
            return true
        }

        // Hoàn lần đầu: thành công, user bị trừ 50 điểm
        val successRevoke = revokePoints("B2001")
        assertTrue(successRevoke)
        assertEquals(100L, user.points)

        // Hoàn lần 2: bị chặn, điểm giữ nguyên
        val duplicateRevoke = revokePoints("B2001")
        assertFalse(duplicateRevoke)
        assertEquals(100L, user.points)
    }
}
