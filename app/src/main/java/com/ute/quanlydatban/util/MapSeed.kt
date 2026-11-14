package com.ute.quanlydatban.util

import com.google.firebase.firestore.FirebaseFirestore

// Đồ thị đường đi trong quán mẫu r1 (2 tầng) để tìm đường bằng BFS.
// Id nút phòng/bàn trùng id bàn (r1_t5 = Phòng VIP 2), id nút QR trùng id qrLocations.
object MapSeed {
    private const val RID = "r1"

    private data class N(val id: String, val floor: Int, val x: Double, val y: Double)

    private val nodes = listOf(
        N("r1_qr_door", 1, 50.0, 92.0),
        N("r1_n1_hall", 1, 50.0, 55.0),
        N("r1_n1_stairs", 1, 80.0, 85.0),
        N("r1_t1", 1, 20.0, 30.0),
        N("r1_t2", 1, 80.0, 30.0),
        N("r1_t3", 1, 20.0, 70.0),
        N("r1_qr_stairs2", 2, 30.0, 85.0),
        N("r1_n2_c1", 2, 48.0, 85.0),
        N("r1_n2_c2", 2, 48.0, 58.0),
        N("r1_n2_c3", 2, 48.0, 27.0),
        N("r1_t4", 2, 22.0, 27.0),
        N("r1_t5", 2, 78.0, 27.0),
        N("r1_t6", 2, 22.0, 58.0),
        N("r1_t7", 2, 78.0, 58.0)
    )

    // Mỗi cạnh khai báo một lần, code tự nối hai chiều
    private val edges = listOf(
        "r1_qr_door" to "r1_n1_hall",
        "r1_n1_hall" to "r1_t1", "r1_n1_hall" to "r1_t2", "r1_n1_hall" to "r1_t3",
        "r1_n1_hall" to "r1_n1_stairs",
        "r1_n1_stairs" to "r1_qr_stairs2",       // cầu thang nối tầng 1 và 2
        "r1_qr_stairs2" to "r1_n2_c1",
        "r1_n2_c1" to "r1_n2_c2",
        "r1_n2_c2" to "r1_n2_c3",
        "r1_n2_c2" to "r1_t6", "r1_n2_c2" to "r1_t7",
        "r1_n2_c3" to "r1_t4", "r1_n2_c3" to "r1_t5"
    )

    fun load(db: FirebaseFirestore, onResult: (Boolean) -> Unit) {
        val neighbors = nodes.associate { it.id to mutableListOf<String>() }
        edges.forEach { (a, b) ->
            neighbors.getValue(a).add(b)
            neighbors.getValue(b).add(a)
        }

        val batch = db.batch()
        nodes.forEach { n ->
            batch.set(db.collection("mapNodes").document(n.id), mapOf(
                "id" to n.id, "restaurantId" to RID, "floor" to n.floor,
                "x" to n.x, "y" to n.y, "neighbors" to neighbors.getValue(n.id)
            ))
        }

        // QR vị trí: dán ở cửa chính và chân cầu thang tầng 2
        listOf(
            mapOf("id" to "r1_qr_door", "floor" to 1, "label" to "Cửa chính", "x" to 50.0, "y" to 92.0),
            mapOf("id" to "r1_qr_stairs2", "floor" to 2, "label" to "Cầu thang tầng 2", "x" to 30.0, "y" to 85.0)
        ).forEach { q ->
            batch.set(
                db.collection("qrLocations").document(q["id"] as String),
                q + mapOf("restaurantId" to RID)
            )
        }

        batch.commit()
            .addOnSuccessListener { onResult(true) }
            .addOnFailureListener { onResult(false) }
    }
}