package com.ute.quanlydatban

import com.ute.quanlydatban.data.model.MenuItem
import com.ute.quanlydatban.data.model.OrderItem
import com.ute.quanlydatban.viewmodel.MenuValidationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnerMenuUnitTest {

    private fun validateInput(name: String, priceStr: String, category: String): MenuValidationResult {
        var nameErr: String? = null
        var priceErr: String? = null
        var catErr: String? = null

        if (name.trim().isEmpty()) {
            nameErr = "Tên món không được để trống"
        }

        val priceLong = priceStr.trim().toLongOrNull()
        if (priceLong == null || priceLong <= 0L) {
            priceErr = "Giá món phải là số lớn hơn 0"
        }

        if (category.trim().isEmpty()) {
            catErr = "Vui lòng chọn hoặc nhập danh mục"
        }

        return if (nameErr == null && priceErr == null && catErr == null) {
            MenuValidationResult.Valid
        } else {
            MenuValidationResult.Invalid(nameErr, priceErr, catErr)
        }
    }

    @Test
    fun testValidateEmptyName() {
        val result1 = validateInput("", "50000", "Món chính")
        assertTrue(result1 is MenuValidationResult.Invalid)
        assertEquals("Tên món không được để trống", (result1 as MenuValidationResult.Invalid).nameError)

        val result2 = validateInput("   ", "50000", "Món chính")
        assertTrue(result2 is MenuValidationResult.Invalid)
        assertEquals("Tên món không được để trống", (result2 as MenuValidationResult.Invalid).nameError)
    }

    @Test
    fun testValidateInvalidPriceZeroOrNegative() {
        val resZero = validateInput("Bò né", "0", "Món chính")
        assertTrue(resZero is MenuValidationResult.Invalid)
        assertEquals("Giá món phải là số lớn hơn 0", (resZero as MenuValidationResult.Invalid).priceError)

        val resNeg = validateInput("Bò né", "-20000", "Món chính")
        assertTrue(resNeg is MenuValidationResult.Invalid)
        assertEquals("Giá món phải là số lớn hơn 0", (resNeg as MenuValidationResult.Invalid).priceError)

        val resNan = validateInput("Bò né", "abc", "Món chính")
        assertTrue(resNan is MenuValidationResult.Invalid)
        assertEquals("Giá món phải là số lớn hơn 0", (resNan as MenuValidationResult.Invalid).priceError)
    }

    @Test
    fun testValidateEmptyCategory() {
        val resCat = validateInput("Bò né", "50000", "")
        assertTrue(resCat is MenuValidationResult.Invalid)
        assertEquals("Vui lòng chọn hoặc nhập danh mục", (resCat as MenuValidationResult.Invalid).categoryError)
    }

    @Test
    fun testValidateValidInput() {
        val result = validateInput("Bò né hoa đăng", "85000", "Món chính")
        assertTrue(result is MenuValidationResult.Valid)
    }

    @Test
    fun testPreserveOrderItemUnitPriceWhenMenuItemPriceChanges() {
        val initialDish = MenuItem(
            id = "dish_1",
            restaurantId = "res_1",
            name = "Lẩu Thái hải sản",
            price = 250_000L,
            category = "Món lẩu",
            available = true
        )

        // Khách đặt món và lưu vào orderItems với unitPrice cố định tại thời điểm đặt
        val orderItem = OrderItem(
            id = "order_item_1",
            bookingId = "booking_101",
            menuItemId = initialDish.id,
            name = initialDish.name,
            qty = 2L,
            unitPrice = initialDish.price
        )

        val initialTotal = orderItem.qty * orderItem.unitPrice
        assertEquals(500_000L, initialTotal)

        // Chủ quán tăng giá món trong menuItems
        val updatedDish = initialDish.copy(price = 320_000L)

        // Đơn cũ không bị ảnh hưởng vì unitPrice được lưu riêng
        assertEquals(250_000L, orderItem.unitPrice)
        assertEquals(500_000L, orderItem.qty * orderItem.unitPrice)
        assertEquals(320_000L, updatedDish.price)
    }

    @Test
    fun testCategoryFiltering() {
        val items = listOf(
            MenuItem(id = "1", name = "Gỏi ngó sen", category = "Khai vị", price = 65_000L),
            MenuItem(id = "2", name = "Bò né", category = "Món chính", price = 95_000L),
            MenuItem(id = "3", name = "Cơm chiên cá mặn", category = "Món chính", price = 80_000L),
            MenuItem(id = "4", name = "Chè hạt sen", category = "Tráng miệng", price = 30_000L)
        )

        val mainDishes = items.filter { it.category == "Món chính" }
        assertEquals(2, mainDishes.size)

        val desserts = items.filter { it.category == "Tráng miệng" }
        assertEquals(1, desserts.size)
    }

    @Test
    fun testAvailabilityToggle() {
        val item = MenuItem(
            id = "1",
            name = "Bò sốt vang",
            price = 85_000L,
            available = true
        )
        assertTrue(item.available)

        val outOfStockItem = item.copy(available = false)
        assertFalse(outOfStockItem.available)
    }
}
