package it.unibo.collektive.utils

import android.location.Location
import it.unibo.collektive.stdlib.util.Point3D

class CoordinatesGenerator {
     fun generateLocationAtDistance(
        baseLat: Double,
        baseLon: Double,
        distanceMeters: Double,
        timeProvider: TestTimeProvider,
        provider: String = "mock"
    ): Location {
        val generator = ECEFCoordinatesGenerator()
        val location = Location(provider)
        val baseECEF = generator.latLonAltToECEF(baseLat, baseLon, 0.0)
        val newECEF = Point3D(Triple(baseECEF.x + distanceMeters, baseECEF.y, baseECEF.z))
        val (newLat, newLon, newAlt) = generator.ECEFToLatLonAlt(newECEF)
        location.latitude = newLat
        location.longitude = newLon
        location.altitude = newAlt
        location.accuracy = 1f
        location.time = timeProvider.currentTimeMillis()
        return location
    }
}
