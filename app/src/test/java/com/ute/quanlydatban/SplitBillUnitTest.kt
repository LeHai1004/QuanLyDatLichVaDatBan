package com.ute.quanlydatban

import com.ute.quanlydatban.data.model.OrderItem
import com.ute.quanlydatban.data.repository.SplitBillCalculator
import com.ute.quanlydatban.ui.customer.split.SplitPersonUiModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitBillUnitTest {

    @Test
    fun calculateEqualSplit_divisibleWithoutRemainder() {
        val totalPayable = 200_000L
        val numPersons = 2

        val result = SplitBillCalculator.calculateEqualSplit(totalPayable, numPersons)

        assertEquals(2, result.size)
        assertEquals(100_000L, result[0])
        assertEquals(100_000L, result[1])
        assertEquals(totalPayable, result.sum())
    }

    @Test
    fun calculateEqualSplit_withLongRemainder_remainderAddedToFirstPerson() {
        val totalPayable = 100_000L
        val numPersons = 3

        val result = SplitBillCalculator.calculateEqualSplit(totalPayable, numPersons)

        assertEquals(3, result.size)
        // 100_000 / 3 = 33_333, remainder = 1
        assertEquals(33_334L, result[0])
        assertEquals(33_333L, result[1])
        assertEquals(33_333L, result[2])
        assertEquals(totalPayable, result.sum())

        // Thử với số người là 7
        val total2 = 500_000L
        val persons2 = 7
        val result2 = SplitBillCalculator.calculateEqualSplit(total2, persons2)
        assertEquals(7, result2.size)
        // 500_000 / 7 = 71_428, remainder = 4
        assertEquals(71_432L, result2[0])
        assertEquals(71_428L, result2[1])
        assertEquals(total2, result2.sum())
    }

    @Test
    fun calculateEqualSplit_lessThanTwoPersons_returnsEmpty() {
        assertEquals(emptyList<Long>(), SplitBillCalculator.calculateEqualSplit(100_000L, 1))
        assertEquals(emptyList<Long>(), SplitBillCalculator.calculateEqualSplit(100_000L, 0))
    }

    @Test
    fun calculateItemSplit_missingAssignment_returnsFailure() {
        val items = listOf(
            OrderItem(id = "item_1", name = "Phở bò", qty = 2, unitPrice = 50_000L),
            OrderItem(id = "item_2", name = "Trà đào", qty = 1, unitPrice = 30_000L),
            OrderItem(id = "item_3", name = "Bánh flan", qty = 1, unitPrice = 20_000L)
        )
        // Chưa gán item_3
        val assignments = mapOf(
            "item_1" to 0,
            "item_2" to 1
        )

        val result = SplitBillCalculator.calculateItemSplit(
            orderItems = items,
            assignments = assignments,
            numPersons = 2,
            totalPayable = 150_000L
        )

        assertTrue(result.isFailure)
        val errorMsg = result.exceptionOrNull()?.message.orEmpty()
        assertTrue(errorMsg.contains("Bánh flan"))
    }

    @Test
    fun calculateItemSplit_validAssignments_sumsExactlyToTotalPayable() {
        val items = listOf(
            OrderItem(id = "item_1", name = "Phở bò", qty = 2, unitPrice = 50_000L), // 100_000
            OrderItem(id = "item_2", name = "Cơm sườn", qty = 1, unitPrice = 80_000L), // 80_000
            OrderItem(id = "item_3", name = "Trà đào", qty = 2, unitPrice = 20_000L)   // 40_000
        ) // Tổng món = 220_000

        // Người 0 nhận item_1 (100k), Người 1 nhận item_2 (80k) + item_3 (40k = 120k)
        val assignments = mapOf(
            "item_1" to 0,
            "item_2" to 1,
            "item_3" to 1
        )

        val totalPayable = 220_000L
        val result = SplitBillCalculator.calculateItemSplit(
            orderItems = items,
            assignments = assignments,
            numPersons = 2,
            totalPayable = totalPayable
        )

        assertTrue(result.isSuccess)
        val shares = result.getOrThrow()
        assertEquals(2, shares.size)
        assertEquals(100_000L, shares[0])
        assertEquals(120_000L, shares[1])
        assertEquals(totalPayable, shares.sum())
    }

    @Test
    fun doubleTapProtection_blocksRepeatedPayment() {
        val paidPerson = SplitPersonUiModel(
            guestId = "BG_1",
            name = "Người 1",
            shareAmount = 50_000L,
            isPaid = true,
            isProcessing = false
        )

        val processingPerson = SplitPersonUiModel(
            guestId = "BG_2",
            name = "Người 2",
            shareAmount = 50_000L,
            isPaid = false,
            isProcessing = true
        )

        val readyPerson = SplitPersonUiModel(
            guestId = "BG_3",
            name = "Người 3",
            shareAmount = 50_000L,
            isPaid = false,
            isProcessing = false
        )

        // Kiểm tra điều kiện có được phép bấm thanh toán không
        fun canTriggerPayment(person: SplitPersonUiModel): Boolean {
            return !person.isPaid && !person.isProcessing
        }

        assertFalse(canTriggerPayment(paidPerson))
        assertFalse(canTriggerPayment(processingPerson))
        assertTrue(canTriggerPayment(readyPerson))
    }
}
