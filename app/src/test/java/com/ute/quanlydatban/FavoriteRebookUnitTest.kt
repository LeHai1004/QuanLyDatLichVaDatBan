package com.ute.quanlydatban

import com.ute.quanlydatban.data.model.Favorite
import com.ute.quanlydatban.data.model.MenuItem
import com.ute.quanlydatban.data.model.OrderItem
import com.ute.quanlydatban.data.repository.FavoriteRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoriteRebookUnitTest {

    @Test
    fun testFavoriteDocIdFormat() {
        val userId = "user_abc_123"
        val restaurantId = "res_xyz_789"
        val expectedDocId = "user_abc_123_res_xyz_789"

        val docId = FavoriteRepository.createDocId(userId, restaurantId)
        assertEquals(expectedDocId, docId)
    }

    @Test
    fun testToggleFavoriteIdempotencyAndNoDuplicates() {
        val favoritesDatabase = mutableMapOf<String, Favorite>()

        fun toggle(userId: String, resId: String): Boolean {
            val docId = FavoriteRepository.createDocId(userId, resId)
            return if (favoritesDatabase.containsKey(docId)) {
                favoritesDatabase.remove(docId)
                false // Bỏ thích
            } else {
                favoritesDatabase[docId] = Favorite(
                    id = docId,
                    userId = userId,
                    restaurantId = resId,
                    createdAt = System.currentTimeMillis()
                )
                true // Đã thích
            }
        }

        val uId = "U1"
        val rId = "R1"
        val docKey = "${uId}_${rId}"

        // Lần 1: Thêm vào yêu thích
        val res1 = toggle(uId, rId)
        assertTrue(res1)
        assertEquals(1, favoritesDatabase.size)
        assertTrue(favoritesDatabase.containsKey(docKey))

        // Lần 2: Bấm lại -> Bỏ thích
        val res2 = toggle(uId, rId)
        assertFalse(res2)
        assertEquals(0, favoritesDatabase.size)

        // Lần 3: Bấm lại -> Thêm lại vào yêu thích
        val res3 = toggle(uId, rId)
        assertTrue(res3)
        assertEquals(1, favoritesDatabase.size)

        // Đảm bảo không bao giờ có duplicate records
        val userFavorites = favoritesDatabase.values.filter { it.userId == uId && it.restaurantId == rId }
        assertEquals(1, userFavorites.size)
    }

    @Test
    fun testRebookFilteringUnavailableItemsAndUpdatingPriceWithLong() {
        // Đơn cũ gồm 3 món
        val oldOrderItems = listOf(
            OrderItem(id = "o1", menuItemId = "m1", name = "Lẩu hải sản", qty = 1L, unitPrice = 250_000L),
            OrderItem(id = "o2", menuItemId = "m2", name = "Nem chua rán", qty = 2L, unitPrice = 50_000L),
            OrderItem(id = "o3", menuItemId = "m3", name = "Nước cam ép", qty = 2L, unitPrice = 30_000L)
        )

        // Thực đơn hiện tại của quán
        val currentMenuItems = listOf(
            MenuItem(id = "m1", name = "Lẩu hải sản", price = 280_000L, available = true), // Giá tăng lên 280k
            MenuItem(id = "m2", name = "Nem chua rán", price = 50_000L, available = false), // Hết món
            MenuItem(id = "m4", name = "Trà đào", price = 35_000L, available = true) // Món mới, m3 đã bị xóa
        ).associateBy { it.id }

        var droppedCount = 0
        var priceChanged = false
        val validItems = mutableListOf<OrderItem>()

        for (old in oldOrderItems) {
            val dish = currentMenuItems[old.menuItemId]
            if (dish == null || !dish.available) {
                droppedCount++
            } else {
                if (dish.price != old.unitPrice) {
                    priceChanged = true
                }
                validItems.add(old.copy(
                    unitPrice = dish.price,
                    name = dish.name
                ))
            }
        }

        // Kiểm tra kết quả
        assertEquals(2, droppedCount) // m2 (hết món) và m3 (bị xóa)
        assertTrue(priceChanged) // m1 giá đổi từ 250k -> 280k
        assertEquals(1, validItems.size)

        val rebookDish = validItems.first()
        assertEquals("m1", rebookDish.menuItemId)
        assertEquals(280_000L, rebookDish.unitPrice)
        assertEquals(1L, rebookDish.qty)

        val newTotal = validItems.sumOf { it.qty * it.unitPrice }
        assertEquals(280_000L, newTotal)
    }

    @Test
    fun testRebookAllItemsUnavailable() {
        val oldOrderItems = listOf(
            OrderItem(id = "o1", menuItemId = "m1", name = "Cua sốt me", qty = 1L, unitPrice = 350_000L),
            OrderItem(id = "o2", menuItemId = "m2", name = "Bia thủ công", qty = 4L, unitPrice = 45_000L)
        )

        // Thực đơn hiện tại: cả 2 món đều hết
        val currentMenuItems = listOf(
            MenuItem(id = "m1", name = "Cua sốt me", price = 350_000L, available = false),
            MenuItem(id = "m2", name = "Bia thủ công", price = 45_000L, available = false)
        ).associateBy { it.id }

        var droppedCount = 0
        val validItems = mutableListOf<OrderItem>()

        for (old in oldOrderItems) {
            val dish = currentMenuItems[old.menuItemId]
            if (dish == null || !dish.available) {
                droppedCount++
            } else {
                validItems.add(old)
            }
        }

        assertEquals(2, droppedCount)
        assertTrue(validItems.isEmpty())
    }
}
