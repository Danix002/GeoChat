package it.unibo.collektive.utils

import it.unibo.collektive.stdlib.util.Point3D

class ECEFCoordinatesGenerator {
    fun latLonAltToECEF(lat: Double, lon: Double, alt: Double): Point3D {
        val a = 6378137.0
        val e2 = 6.69437999014e-3
        val radLat = Math.toRadians(lat)
        val radLon = Math.toRadians(lon)
        val N = a / Math.sqrt(1 - e2 * Math.sin(radLat) * Math.sin(radLat))
        val x = (N + alt) * Math.cos(radLat) * Math.cos(radLon)
        val y = (N + alt) * Math.cos(radLat) * Math.sin(radLon)
        val z = (N * (1 - e2) + alt) * Math.sin(radLat)
        return Point3D(Triple(x, y, z))
    }

    fun ECEFToLatLonAlt(point: Point3D): Triple<Double, Double, Double> {
        val a = 6378137.0
        val e2 = 6.69437999014e-3
        val ePrime2 = e2 / (1 - e2)
        val x = point.x
        val y = point.y
        val z = point.z
        val p = Math.sqrt(x * x + y * y)
        val theta = Math.atan2(z * a, p * (1 - e2) * a)
        val sinTheta = Math.sin(theta)
        val cosTheta = Math.cos(theta)
        val lat = Math.atan2(z + ePrime2 * a * sinTheta * sinTheta * sinTheta,
            p - e2 * a * cosTheta * cosTheta * cosTheta)
        val lon = Math.atan2(y, x)
        val N = a / Math.sqrt(1 - e2 * Math.sin(lat) * Math.sin(lat))
        val alt = p / Math.cos(lat) - N
        return Triple(Math.toDegrees(lat), Math.toDegrees(lon), alt)
    }
}
