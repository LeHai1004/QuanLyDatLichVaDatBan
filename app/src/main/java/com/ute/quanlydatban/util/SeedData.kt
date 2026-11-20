package com.ute.quanlydatban.util

import com.google.firebase.firestore.FirebaseFirestore

// Dữ liệu mẫu dùng chung cho cả nhóm. Id cố định để nạp lại không bị trùng.
object SeedData {
    private const val DEPOSIT = 100_000L

    private data class R(
        val id: String, val name: String, val address: String,
        val category: String, val lat: Double, val lng: Double, val rating: Double
    )

    private data class T(
        val name: String, val floor: Int, val type: String, val capacity: Int,
        val x: Double, val y: Double
    )
    private data class M(val name: String, val price: Long, val category: String)

    private val restaurants = listOf(
        R("r1", "Bếp Nhà · Thảo Điền", "12 Nguyễn Văn Hưởng, TP. Hồ Chí Minh", "Món Việt", 10.8031, 106.7377, 4.7),
        R("r2", "Cà Phê Góc Phố", "25 Lê Lợi, Quận 1, TP. Hồ Chí Minh", "Cà phê", 10.7745, 106.7011, 4.5),
        R("r3", "Lẩu Nướng Sài Gòn", "88 Nguyễn Trãi, Quận 5, TP. Hồ Chí Minh", "Lẩu & nướng", 10.7540, 106.6680, 4.6)
    )

    // Mỗi quán dùng chung bộ bàn này: 2 tầng, có bàn và phòng
    // Tọa độ x, y chuẩn hóa 0-100 trên khung sơ đồ tầng (khớp frame IndoorMap)
    private val tables = listOf(
        T("Bàn 1", 1, "table", 2, 20.0, 30.0),
        T("Bàn 2", 1, "table", 4, 80.0, 30.0),
        T("Bàn 3", 1, "table", 4, 20.0, 70.0),
        T("Phòng VIP 1", 2, "room", 8, 22.0, 27.0),
        T("Phòng VIP 2", 2, "room", 10, 78.0, 27.0),
        T("Phòng VIP 3", 2, "room", 8, 22.0, 58.0),
        T("Phòng VIP 4", 2, "room", 10, 78.0, 58.0)
    )

    private val menus = mapOf(
        "r1" to listOf(
            M("Gỏi cuốn tôm thịt", 69_000, "Khai vị"),
            M("Chả giò", 59_000, "Khai vị"),
            M("Cơm chiên hải sản", 129_000, "Món chính"),
            M("Bún chả Hà Nội", 85_000, "Món chính"),
            M("Cá kho tộ", 119_000, "Món chính"),
            M("Canh chua cá lóc", 99_000, "Món chính"),
            M("Bò lúc lắc", 159_000, "Món chính"),
            M("Trà sen", 45_000, "Đồ uống"),
            M("Nước ép cam", 39_000, "Đồ uống"),
            M("Chè khúc bạch", 49_000, "Tráng miệng")
        ),
        "r2" to listOf(
            M("Cà phê sữa", 35_000, "Đồ uống"),
            M("Bạc xỉu", 39_000, "Đồ uống"),
            M("Trà đào cam sả", 45_000, "Đồ uống"),
            M("Bánh tiramisu", 55_000, "Tráng miệng"),
            M("Croissant bơ", 49_000, "Bánh")
        ),
        "r3" to listOf(
            M("Lẩu thái hải sản", 289_000, "Lẩu"),
            M("Bò nướng lá lốt", 129_000, "Nướng"),
            M("Ba chỉ nướng", 99_000, "Nướng"),
            M("Rau nhúng lẩu", 59_000, "Khai vị"),
            M("Nước ngọt", 25_000, "Đồ uống")
        )
    )

    fun load(db: FirebaseFirestore, onResult: (Boolean) -> Unit) {
        val batch = db.batch()

        restaurants.forEach { r ->
            val rRef = db.collection("restaurants").document(r.id)
            batch.set(rRef, mapOf(
                "id" to r.id, "ownerId" to "", "name" to r.name, "address" to r.address,
                "category" to r.category, "lat" to r.lat, "lng" to r.lng,
                "openTime" to "09:00", "closeTime" to "22:00",
                "rating" to r.rating, "depositAmount" to DEPOSIT
            ))
            tables.forEachIndexed { i, t ->
                val id = "${r.id}_t${i + 1}"
                batch.set(rRef.collection("tables").document(id), mapOf(
                    "id" to id, "name" to t.name, "floor" to t.floor, "type" to t.type,
                    "capacity" to t.capacity, "mapX" to t.x, "mapY" to t.y,
                    "status" to "available", "qrCode" to id
                ))
            }
        }

        menus.forEach { (rid, items) ->
            items.forEachIndexed { i, m ->
                val id = "${rid}_m${i + 1}"
                batch.set(db.collection("menuItems").document(id), mapOf(
                    "id" to id, "restaurantId" to rid, "name" to m.name,
                    "price" to m.price, "category" to m.category,
                    "imageUrl" to "", "available" to true
                ))
            }
        }

        val expGeneral = 1830211200000L // 31/12/2027
        val expBep20k = 1796057999000L  // 30/11/2026
        val expHen10 = 1797353999000L   // 15/12/2026
        val expBanNgon = 1798736399000L// 31/12/2026

        listOf(
            mapOf("id" to "v_WELCOME20", "code" to "WELCOME20", "type" to "amount", "value" to 20_000L, "minOrder" to 100_000L, "maxDiscount" to 0L, "expiry" to expGeneral, "active" to true, "restaurantId" to "", "pointsCost" to 0L),
            mapOf("id" to "v_GIAM10", "code" to "GIAM10", "type" to "percent", "value" to 10L, "minOrder" to 200_000L, "maxDiscount" to 50_000L, "expiry" to expGeneral, "active" to true, "restaurantId" to "", "pointsCost" to 100L),
            mapOf("id" to "v_VIP50", "code" to "VIP50", "type" to "amount", "value" to 50_000L, "minOrder" to 300_000L, "maxDiscount" to 0L, "expiry" to expGeneral, "active" to true, "restaurantId" to "", "pointsCost" to 300L),
            mapOf("id" to "v_BEP20K", "code" to "BEP20K", "type" to "amount", "value" to 20_000L, "minOrder" to 200_000L, "maxDiscount" to 0L, "expiry" to expBep20k, "active" to true, "restaurantId" to "", "pointsCost" to 0L),
            mapOf("id" to "v_HEN10", "code" to "HEN10", "type" to "percent", "value" to 10L, "minOrder" to 0L, "maxDiscount" to 50_000L, "expiry" to expHen10, "active" to true, "restaurantId" to "", "pointsCost" to 0L),
            mapOf("id" to "v_BANNGON10K", "code" to "BANNGON10K", "type" to "amount", "value" to 10_000L, "minOrder" to 100_000L, "maxDiscount" to 0L, "expiry" to expBanNgon, "active" to true, "restaurantId" to "", "pointsCost" to 0L)
        ).forEach { v ->
            val id = v["id"] as String
            batch.set(db.collection("vouchers").document(id), v)
        }

        batch.commit()
            .addOnSuccessListener { onResult(true) }
            .addOnFailureListener { onResult(false) }
    }
}
