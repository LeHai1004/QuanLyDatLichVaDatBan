package com.ute.quanlydatban.util

import android.graphics.PointF
import com.ute.quanlydatban.data.model.MapNode
import java.util.ArrayDeque
import kotlin.math.hypot

data class PathResult(
    val fullNodes: List<MapNode>,
    val pathOnCurrentFloor: List<PointF>,
    val requiresFloorChange: Boolean,
    val targetFloor: Long,
    val distanceMeters: Int,
    val instruction: String
)

object PathFinder {

    fun findPath(
        nodes: List<MapNode>,
        startX: Double,
        startY: Double,
        startFloor: Long,
        targetX: Double,
        targetY: Double,
        targetFloor: Long,
        displayFloor: Long
    ): PathResult? {
        if (nodes.isEmpty()) return null

        val nodeMap = nodes.associateBy { it.id }

        // 1. Tìm node gần nhất với điểm xuất phát trên tầng xuất phát
        val startNodes = nodes.filter { it.floor == startFloor }
        val nearestStart = startNodes.minByOrNull { hypot(it.x - startX, it.y - startY) } ?: return null

        // 2. Tìm node gần nhất với điểm đến trên tầng của bàn
        val targetNodes = nodes.filter { it.floor == targetFloor }
        val nearestTarget = targetNodes.minByOrNull { hypot(it.x - targetX, it.y - targetY) } ?: return null

        // 3. BFS
        val queue = ArrayDeque<String>()
        val visited = mutableSetOf<String>()
        val parent = mutableMapOf<String, String>()

        queue.add(nearestStart.id)
        visited.add(nearestStart.id)

        var found = false
        while (queue.isNotEmpty()) {
            val currId = queue.poll() ?: break
            if (currId == nearestTarget.id) {
                found = true
                break
            }

            val currNode = nodeMap[currId] ?: continue
            for (neighborId in currNode.neighbors) {
                if (neighborId !in visited && neighborId in nodeMap) {
                    visited.add(neighborId)
                    parent[neighborId] = currId
                    queue.add(neighborId)
                }
            }
        }

        if (!found) return null

        // Tái tạo đường đi từ start đến target
        val pathIds = mutableListOf<String>()
        var curr: String? = nearestTarget.id
        while (curr != null) {
            pathIds.add(0, curr)
            curr = parent[curr]
        }

        val fullNodes = pathIds.mapNotNull { nodeMap[it] }

        // Tính toán tọa độ đường đi trên tầng đang hiển thị (displayFloor)
        val pathPoints = mutableListOf<PointF>()
        // Nếu điểm xuất phát nằm trên tầng này, thêm điểm xuất phát
        if (startFloor == displayFloor) {
            pathPoints.add(PointF(startX.toFloat(), startY.toFloat()))
        }

        fullNodes.filter { it.floor == displayFloor }.forEach {
            pathPoints.add(PointF(it.x.toFloat(), it.y.toFloat()))
        }

        // Nếu điểm đến nằm trên tầng này, thêm điểm đến
        if (targetFloor == displayFloor) {
            pathPoints.add(PointF(targetX.toFloat(), targetY.toFloat()))
        }

        // Tính khoảng cách ước lượng
        var totalDist = 0.0
        for (i in 0 until fullNodes.size - 1) {
            val n1 = fullNodes[i]
            val n2 = fullNodes[i + 1]
            if (n1.floor == n2.floor) {
                totalDist += hypot(n2.x - n1.x, n2.y - n1.y)
            } else {
                totalDist += 15.0 // Phạt khoảng cách đi cầu thang
            }
        }
        val distanceMeters = (totalDist * 0.4).toInt().coerceAtLeast(5)

        val requiresFloorChange = startFloor != targetFloor

        val instruction = when {
            requiresFloorChange && displayFloor == startFloor -> {
                if (targetFloor > startFloor) "Đi theo hướng cầu thang để lên Tầng $targetFloor"
                else "Đi theo hướng cầu thang để xuống Tầng $targetFloor"
            }
            requiresFloorChange && displayFloor == targetFloor -> {
                "Đi từ cầu thang đến phòng đã đặt"
            }
            else -> {
                "Theo đường nét đứt để đến phòng của bạn"
            }
        }

        return PathResult(
            fullNodes = fullNodes,
            pathOnCurrentFloor = pathPoints,
            requiresFloorChange = requiresFloorChange,
            targetFloor = targetFloor,
            distanceMeters = distanceMeters,
            instruction = instruction
        )
    }
}
