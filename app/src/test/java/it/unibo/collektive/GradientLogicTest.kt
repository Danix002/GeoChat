package it.unibo.collektive

import it.unibo.collektive.stdlib.util.Point3D
import kotlin.math.*
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class GradientLogicTest {
    @Test
    fun `gradient logic accepts point within range`() {
        val maxDistance = 10_000f
        val source = randomPoint3D()
        val neighbor = randomPoint3DNearby(source, maxDistance)
        val fromSource = 0.0
        val toNeighbor = testEuclideanDistance3D(source, neighbor)
        val accepted = fromSource + toNeighbor <= maxDistance.toDouble()
        assertTrue("Expected message to be accepted", accepted)
    }

    @Test
    fun `gradient logic rejects point beyond range`() {
        val maxDistance = 10_000f
        val source = randomPoint3D()
        val neighbor = randomPoint3DNearby(source, maxDistance * 1.5f)
        val fromSource = 0.0
        val toNeighbor = testEuclideanDistance3D(source, neighbor)
        val accepted = fromSource + toNeighbor <= maxDistance.toDouble()
        assertFalse("Expected message to be rejected", accepted)
    }

    @Test
    fun `gradient accumulate logic passes only within range`() {
        val maxDistance = 5000.0
        val cases = listOf(
            Triple(0.0, 1000.0, true),      // sender to neighbor
            Triple(3000.0, 1500.0, true),   // intermediate node
            Triple(3000.0, 2500.0, false),  // exceeds distance
            Triple(5000.0, 0.0, true),      // edge case, exactly at limit
            Triple(4999.9, 0.11, false)     // just over limit
        )
        for ((fromSource, toNeighbor, expected) in cases) {
            val total = fromSource + toNeighbor
            val accepted = total <= maxDistance
            assertEquals("Expected $expected for total $total", expected, accepted)
        }
    }

    private fun randomPoint3D(): Point3D {
        val lat = Random.nextDouble(-90.0, 90.0)
        val lon = Random.nextDouble(-180.0, 180.0)
        val alt = Random.nextDouble(0.0, 1000.0)
        return Point3D(Triple(lat, lon, alt))
    }

    private fun randomPoint3DNearby(base: Point3D, maxDistanceMeters: Float = 10_000f): Point3D {
        val maxLatOffset = maxDistanceMeters / 111_000.0
        val maxLonOffset = maxDistanceMeters / (111_000.0 * cos(Math.toRadians(base.x)))
        val lat = base.x + Random.nextDouble(-maxLatOffset, maxLatOffset)
        val lon = base.y + Random.nextDouble(-maxLonOffset, maxLonOffset)
        val alt = base.z + Random.nextDouble(-10.0, 10.0)
        return Point3D(Triple(lat, lon, alt))
    }

    private fun testEuclideanDistance3D(p1: Point3D, p2: Point3D): Double {
        val earthRadiusMeters = 111_000.0
        val dLat = (p2.x - p1.x) * earthRadiusMeters
        val dLon = (p2.y - p1.y) * earthRadiusMeters * cos(Math.toRadians(p1.y))
        val dAlt = p2.z - p1.z
        return sqrt(dLat * dLat + dLon * dLon + dAlt * dAlt)
    }
}

