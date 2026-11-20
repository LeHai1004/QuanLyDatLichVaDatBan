package com.ute.quanlydatban

import com.ute.quanlydatban.data.model.MapNode
import com.ute.quanlydatban.util.PathFinder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PathFinderUnitTest {

    private val sampleNodes = listOf(
        // Tầng 1
        MapNode("n_f1_door", "r1", 1, 50.0, 92.0, listOf("n_f1_hall", "n_f1_stairs")),
        MapNode("n_f1_hall", "r1", 1, 50.0, 50.0, listOf("n_f1_door", "n_f1_t1", "n_f1_stairs")),
        MapNode("n_f1_t1", "r1", 1, 20.0, 30.0, listOf("n_f1_hall")),
        MapNode("n_f1_stairs", "r1", 1, 20.0, 85.0, listOf("n_f1_door", "n_f1_hall", "n_f2_stairs")),

        // Tầng 2
        MapNode("n_f2_stairs", "r1", 2, 20.0, 85.0, listOf("n_f1_stairs", "n_f2_corridor")),
        MapNode("n_f2_corridor", "r1", 2, 50.0, 50.0, listOf("n_f2_stairs", "n_f2_vip2")),
        MapNode("n_f2_vip2", "r1", 2, 78.0, 27.0, listOf("n_f2_corridor"))
    )

    @Test
    fun testSameFloorPathFinding() {
        val result = PathFinder.findPath(
            nodes = sampleNodes,
            startX = 50.0,
            startY = 50.0,
            startFloor = 2,
            targetX = 78.0,
            targetY = 27.0,
            targetFloor = 2,
            displayFloor = 2
        )

        assertNotNull(result)
        assertTrue(result!!.fullNodes.isNotEmpty())
        assertEquals(false, result.requiresFloorChange)
        assertEquals(2L, result.targetFloor)
    }

    @Test
    fun testCrossFloorPathFindingViaStairs() {
        val result = PathFinder.findPath(
            nodes = sampleNodes,
            startX = 50.0,
            startY = 92.0,
            startFloor = 1,
            targetX = 78.0,
            targetY = 27.0,
            targetFloor = 2,
            displayFloor = 1
        )

        assertNotNull(result)
        assertEquals(true, result!!.requiresFloorChange)
        assertEquals(2L, result.targetFloor)
        assertTrue(result.fullNodes.any { it.id == "n_f1_stairs" })
        assertTrue(result.fullNodes.any { it.id == "n_f2_stairs" })
    }

    @Test
    fun testDisconnectedGraphReturnsNull() {
        val disconnectedNodes = listOf(
            MapNode("n1", "r1", 1, 10.0, 10.0, emptyList()),
            MapNode("n2", "r1", 1, 90.0, 90.0, emptyList())
        )

        val result = PathFinder.findPath(
            nodes = disconnectedNodes,
            startX = 10.0,
            startY = 10.0,
            startFloor = 1,
            targetX = 90.0,
            targetY = 90.0,
            targetFloor = 1,
            displayFloor = 1
        )

        assertNull(result)
    }
}
