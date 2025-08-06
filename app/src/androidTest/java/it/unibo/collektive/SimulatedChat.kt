package it.unibo.collektive

import io.mockk.every
import io.mockk.spyk
import it.nicolasfarabegoli.mktt.MqttMessage
import it.unibo.collektive.viewmodels.MessagesViewModel
import it.unibo.collektive.viewmodels.NearbyDevicesViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Test
import android.location.Location
import android.util.Log
import it.unibo.collektive.stdlib.util.Point3D
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import kotlin.math.cos
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

class SimulatedChat {
    private val deviceCount = 10
    private val baseLat = Random.nextDouble(-90.0, 90.0)
    private val baseLon = Random.nextDouble(-180.0, 180.0)
    private lateinit var devices: List<Triple<MessagesViewModel, NearbyDevicesViewModel, CoroutineScope>>
    private lateinit var messageFlow: MutableSharedFlow<MqttMessage>

    @Before
    fun setup() = runBlocking {
        messageFlow = MutableSharedFlow(extraBufferCapacity = 100)
        devices = List(deviceCount) { i ->
            val mockLocation = randomLocationNearby(baseLat, baseLon)

            val testScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val nearbyVM = spyk(
                NearbyDevicesViewModel(providedScope = testScope),
                recordPrivateCalls = true
            )
            every { nearbyVM.userName } returns MutableStateFlow("User $i")

            val messagesVM = spyk(MessagesViewModel(), recordPrivateCalls = true)
            messagesVM.setLocation(mockLocation)

            Triple(messagesVM, nearbyVM, testScope)
        }
    }

    @Test
    fun connection() = runBlocking {
        val connectionStates = mutableMapOf<Uuid, MutableList<NearbyDevicesViewModel.ConnectionState>>()
        val jobs = devices.map { device ->
            launch {
                device.second.connectionFlow.collect { state ->
                    Log.i("Connection state", "$state")
                    connectionStates.getOrPut(device.second.deviceId) { mutableListOf() }.add(state)
                }
            }
        }
        devices.forEach { viewModels ->
            viewModels.second.startCollektiveProgram()
        }
        delay(2.seconds)
        connectionStates.forEach { (id, connectionStateList) ->
            val latest = connectionStateList.lastOrNull() ?: NearbyDevicesViewModel.ConnectionState.DISCONNECTED
            assertTrue(latest == NearbyDevicesViewModel.ConnectionState.CONNECTED)
        }
        jobs.forEach { it.cancel() }
    }

    @Test
    fun `found neighbor`() = runBlocking {
        val neighborhoods = mutableMapOf<Uuid, MutableList<Set<Uuid>>>()
        val jobs = devices.map { device ->
            launch {
                device.second.dataFlow.collect { neighbors ->
                    neighborhoods.getOrPut(device.second.deviceId) { mutableListOf() }.add(neighbors)
                }
            }
        }
        devices.forEach { viewModels ->
            viewModels.second.startCollektiveProgram()
        }
        delay(20.seconds)
        neighborhoods.forEach { (id, neighborList) ->
            val latest = neighborList.lastOrNull() ?: emptySet()
            Log.i("Device $id", "sees ${latest.size} neighbors")
            assertTrue(latest.size == deviceCount - 1) // Not count yourself
        }
        jobs.forEach { it.cancel() }
    }

    //@Test
    //fun `real chat simulation`() = runBlocking {

    //}

    private fun randomLocationNearby(
        baseLat: Double,
        baseLon: Double,
        maxDistanceMeters: Double = 10_000.0,
        provider: String = "mock"
    ): Location {
        val location = Location(provider)
        val baseECEF = latLonAltToECEF(baseLat, baseLon, 0.0)
        val newECEF = Point3D(Triple(baseECEF.x, baseECEF.y, baseECEF.z))
        val (newLat, newLon, newAlt) = ecefToLatLonAlt(newECEF)
        val maxLatOffset = maxDistanceMeters / 111_000.0
        val maxLonOffset = maxDistanceMeters / (111_000.0 * cos(Math.toRadians(newLat)))
        location.latitude = newLat + Random.nextDouble(-maxLatOffset, maxLatOffset)
        location.longitude = newLon + Random.nextDouble(-maxLonOffset, maxLonOffset)
        location.altitude = newAlt + Random.nextDouble(0.0, 100.0)
        location.accuracy = 1f
        location.time = System.currentTimeMillis()
        return location
    }

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

    fun ecefToLatLonAlt(point: Point3D): Triple<Double, Double, Double> {
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

    @After
    fun tearDown() {
        devices.forEach {
            it.third.cancel()
        }
    }

}
