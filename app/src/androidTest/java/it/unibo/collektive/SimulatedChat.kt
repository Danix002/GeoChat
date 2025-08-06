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
import it.unibo.collektive.model.Message
import it.unibo.collektive.stdlib.util.Point3D
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import java.time.LocalDateTime
import kotlin.collections.component1
import kotlin.math.cos
import kotlin.random.Random
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

class SimulatedChat {
    private val deviceCount = 4
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

    @Test
    fun `dynamic movement and message propagation`() = runBlocking {
        val messages = mutableMapOf<Uuid, MutableList<List<Message>>>()
        val jobs = mutableListOf<Job>()

        val startTime = System.currentTimeMillis()

        devices.forEachIndexed { i, (messagesVM, nearbyVM, scope) ->
            jobs += launch {
                messagesVM.messages.collect { state ->
                    Log.i("Device ${nearbyVM.deviceId}", "Received messages: $state")
                    messages.getOrPut(nearbyVM.deviceId) { mutableListOf() }.add(state)
                }
            }
            jobs += launch {
                var currentOffset = 0.0
                while (System.currentTimeMillis() - startTime < 60_000) {
                    val newLocation = generateLocationAtDistance(baseLat, baseLon, currentOffset)
                    messagesVM.setLocation(newLocation)
                    currentOffset += 10.0
                    delay(5.seconds)
                }
            }
            jobs += launch {
                val localId = nearbyVM.deviceId.toString()
                var lastAttempt = 0L
                var sentCounter = 0
                while (System.currentTimeMillis() - startTime < 60_000) {
                    val now = System.currentTimeMillis()
                    val inCooldown = now - lastAttempt < 5_000
                    val wasSender = messagesVM.getSendFlag()

                    val shouldBecomeSource = !inCooldown && isSource()

                    when {
                        shouldBecomeSource -> {
                            sentCounter++
                            lastAttempt = now
                            messagesVM.markAsSource(now)
                            val msg = "Hello! I'm device $localId attempt $sentCounter"
                            val dist = Random.nextInt(5000, 10000).toFloat()
                            messagesVM.enqueueMessage(
                                message = msg,
                                time = LocalDateTime.now(),
                                distance = dist,
                                spreadingTime = Random.nextInt(5, 60)
                            )
                            messagesVM.setSendFlag(true)
                        }
                        wasSender && messagesVM.sourceSince.value?.let { now - it < 2_000 } == true -> {
                            // stillSource: do nothing
                        }
                        else -> {
                            if (!inCooldown) messagesVM.clearSourceStatus()
                            messagesVM.setSendFlag(false)
                        }
                    }
                    delay(1.seconds)
                }
            }
            messagesVM.setOnlineStatus(true)
            messagesVM.listenAndSend(nearbyVM, nearbyVM.userName.value)
        }

        delay(1.minutes)

        val devicesReceivedMessages = messages
        devicesReceivedMessages.forEach { (id, receivedMessages) ->
            assertTrue(receivedMessages.isNotEmpty())
        }

        jobs.forEach { it.cancel() }
    }

    fun isSource() = Random.nextFloat() < 0.25f

    private fun randomLocationNearby(
        baseLat: Double,
        baseLon: Double,
        maxDistanceMeters: Double = 5_000.0,
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

    private fun generateLocationAtDistance(
        baseLat: Double,
        baseLon: Double,
        distanceMeters: Double,
        provider: String = "mock"
    ): Location {
        val location = Location(provider)
        val baseECEF = latLonAltToECEF(baseLat, baseLon, 0.0)
        val newECEF = Point3D(Triple(baseECEF.x + distanceMeters, baseECEF.y, baseECEF.z))
        val (newLat, newLon, newAlt) = ecefToLatLonAlt(newECEF)
        location.latitude = newLat
        location.longitude = newLon
        location.altitude = newAlt
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
