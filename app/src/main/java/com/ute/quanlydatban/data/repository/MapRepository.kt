package com.ute.quanlydatban.data.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.ute.quanlydatban.data.model.Booking
import com.ute.quanlydatban.data.model.DiningTable
import com.ute.quanlydatban.data.model.MapNode
import com.ute.quanlydatban.data.model.QrLocation
import com.ute.quanlydatban.data.model.Restaurant

class MapRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    companion object {
        private const val TAG = "MapRepository"
    }

    fun getBooking(bookingId: String, onResult: (Result<Booking?>) -> Unit) {
        if (bookingId.isBlank()) {
            onResult(Result.success(null))
            return
        }
        firestore.collection("bookings").document(bookingId).get()
            .addOnSuccessListener { snapshot ->
                val booking = snapshot.toObject(Booking::class.java)?.copy(id = snapshot.id)
                onResult(Result.success(booking))
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Lỗi đọc booking: ${e.message}", e)
                onResult(Result.failure(e))
            }
    }

    fun getRestaurant(restaurantId: String, onResult: (Result<Restaurant?>) -> Unit) {
        if (restaurantId.isBlank()) {
            onResult(Result.success(null))
            return
        }
        firestore.collection("restaurants").document(restaurantId).get()
            .addOnSuccessListener { snapshot ->
                val res = snapshot.toObject(Restaurant::class.java)?.copy(id = snapshot.id)
                onResult(Result.success(res))
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Lỗi đọc restaurant: ${e.message}", e)
                onResult(Result.failure(e))
            }
    }

    fun getTables(restaurantId: String, onResult: (Result<List<DiningTable>>) -> Unit) {
        if (restaurantId.isBlank()) {
            onResult(Result.success(emptyList()))
            return
        }
        firestore.collection("restaurants").document(restaurantId)
            .collection("tables").get()
            .addOnSuccessListener { snapshot ->
                val tables = snapshot.toObjects(DiningTable::class.java)
                onResult(Result.success(tables))
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Lỗi đọc tables: ${e.message}", e)
                onResult(Result.failure(e))
            }
    }

    fun getMapNodes(restaurantId: String, onResult: (Result<List<MapNode>>) -> Unit) {
        firestore.collection("mapNodes")
            .whereEqualTo("restaurantId", restaurantId)
            .get()
            .addOnSuccessListener { snapshot ->
                val nodes = snapshot.toObjects(MapNode::class.java)
                if (nodes.isNotEmpty()) {
                    onResult(Result.success(nodes))
                } else {
                    onResult(Result.success(getDefaultMapNodes(restaurantId)))
                }
            }
            .addOnFailureListener {
                onResult(Result.success(getDefaultMapNodes(restaurantId)))
            }
    }

    fun getQrLocations(restaurantId: String, onResult: (Result<List<QrLocation>>) -> Unit) {
        firestore.collection("qrLocations")
            .whereEqualTo("restaurantId", restaurantId)
            .get()
            .addOnSuccessListener { snapshot ->
                val locations = snapshot.toObjects(QrLocation::class.java)
                if (locations.isNotEmpty()) {
                    onResult(Result.success(locations))
                } else {
                    onResult(Result.success(getDefaultQrLocations(restaurantId)))
                }
            }
            .addOnFailureListener {
                onResult(Result.success(getDefaultQrLocations(restaurantId)))
            }
    }

    fun findQrLocation(
        qrCode: String,
        restaurantId: String,
        onResult: (Result<QrLocation?>) -> Unit
    ) {
        val clean = qrCode.removePrefix("BOOKTABLE_LOCATION:").trim()
        val locId = if (clean.contains(":")) clean.substringAfter(":") else clean

        firestore.collection("qrLocations").document(locId).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val loc = doc.toObject(QrLocation::class.java)?.copy(id = doc.id)
                    onResult(Result.success(loc))
                } else {
                    firestore.collection("qrLocations")
                        .whereEqualTo("label", locId)
                        .limit(1)
                        .get()
                        .addOnSuccessListener { snap ->
                            if (!snap.isEmpty) {
                                val match = snap.documents[0].toObject(QrLocation::class.java)?.copy(id = snap.documents[0].id)
                                onResult(Result.success(match))
                            } else {
                                val defaultLoc = getDefaultQrLocations(restaurantId).find {
                                    it.id.equals(locId, true) || it.label.equals(locId, true)
                                }
                                onResult(Result.success(defaultLoc))
                            }
                        }
                        .addOnFailureListener {
                            val defaultLoc = getDefaultQrLocations(restaurantId).find {
                                it.id.equals(locId, true) || it.label.equals(locId, true)
                            }
                            onResult(Result.success(defaultLoc))
                        }
                }
            }
            .addOnFailureListener {
                val defaultLoc = getDefaultQrLocations(restaurantId).find {
                    it.id.equals(locId, true) || it.label.equals(locId, true)
                }
                onResult(Result.success(defaultLoc))
            }
    }

    private fun getDefaultMapNodes(restaurantId: String): List<MapNode> {
        return listOf(
            // Tầng 1
            MapNode("n_f1_door", restaurantId, 1, 50.0, 92.0, listOf("n_f1_hall_center", "n_f1_stairs")),
            MapNode("n_f1_hall_center", restaurantId, 1, 50.0, 50.0, listOf("n_f1_door", "n_f1_t1", "n_f1_t2", "n_f1_t3", "n_f1_stairs")),
            MapNode("n_f1_t1", restaurantId, 1, 20.0, 30.0, listOf("n_f1_hall_center")),
            MapNode("n_f1_t2", restaurantId, 1, 80.0, 30.0, listOf("n_f1_hall_center")),
            MapNode("n_f1_t3", restaurantId, 1, 20.0, 70.0, listOf("n_f1_hall_center", "n_f1_stairs")),
            MapNode("n_f1_stairs", restaurantId, 1, 20.0, 85.0, listOf("n_f1_door", "n_f1_hall_center", "n_f1_t3", "n_f2_stairs")),

            // Tầng 2
            MapNode("n_f2_stairs", restaurantId, 2, 20.0, 85.0, listOf("n_f1_stairs", "n_f2_corridor_bottom")),
            MapNode("n_f2_corridor_bottom", restaurantId, 2, 50.0, 85.0, listOf("n_f2_stairs", "n_f2_corridor_center")),
            MapNode("n_f2_corridor_center", restaurantId, 2, 50.0, 50.0, listOf("n_f2_corridor_bottom", "n_f2_corridor_top", "n_f2_vip3", "n_f2_vip4")),
            MapNode("n_f2_corridor_top", restaurantId, 2, 50.0, 25.0, listOf("n_f2_corridor_center", "n_f2_vip1", "n_f2_vip2")),
            MapNode("n_f2_vip1", restaurantId, 2, 25.0, 27.0, listOf("n_f2_corridor_top")),
            MapNode("n_f2_vip2", restaurantId, 2, 75.0, 27.0, listOf("n_f2_corridor_top")),
            MapNode("n_f2_vip3", restaurantId, 2, 25.0, 58.0, listOf("n_f2_corridor_center")),
            MapNode("n_f2_vip4", restaurantId, 2, 75.0, 58.0, listOf("n_f2_corridor_center"))
        )
    }

    private fun getDefaultQrLocations(restaurantId: String): List<QrLocation> {
        return listOf(
            QrLocation("loc_door", restaurantId, 1, "Cửa chính Tầng 1", 50.0, 92.0),
            QrLocation("loc_stairs_f1", restaurantId, 1, "Cầu thang Tầng 1", 20.0, 85.0),
            QrLocation("loc_stairs_f2", restaurantId, 2, "Cầu thang Tầng 2", 20.0, 85.0),
            QrLocation("loc_corridor_f2", restaurantId, 2, "Hành lang phòng VIP Tầng 2", 50.0, 50.0)
        )
    }
}
