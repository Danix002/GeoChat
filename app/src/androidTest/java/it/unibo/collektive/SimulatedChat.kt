package it.unibo.collektive

import io.mockk.every
import io.mockk.spyk
import it.unibo.collektive.viewmodels.MessagesViewModel
import it.unibo.collektive.viewmodels.NearbyDevicesViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Test
import android.location.Location
import android.util.Log
import it.unibo.collektive.model.Message
import it.unibo.collektive.stdlib.util.Point3D
import it.unibo.collektive.utils.TestTimeProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import kotlin.math.cos
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

class SimulatedChat {
    private val deviceCount = 4
    private val durationOfSimulatedChat = 1 // Minutes
    private val baseLat = Random.nextDouble(-90.0, 90.0)
    private val baseLon = Random.nextDouble(-180.0, 180.0)
    private lateinit var devices: List<Pair<MessagesViewModel, NearbyDevicesViewModel>>

    private fun createViewModels(
        dispatcher: CoroutineDispatcher,
        scope: CoroutineScope,
        timeProvider: TestTimeProvider
    ): List<Pair<MessagesViewModel, NearbyDevicesViewModel>> {
        return List(deviceCount) { i ->
            val mockLocation = randomLocationNearby(baseLat, baseLon, timeProvider)
            val nearbyVM = spyk(
                NearbyDevicesViewModel(
                    dispatcher = dispatcher,
                    providedScope = scope
                ),
                recordPrivateCalls = true
            )
            every { nearbyVM.userName } returns MutableStateFlow("User $i")

            val messagesVM = spyk(
                MessagesViewModel(
                    dispatcher = dispatcher,
                    providedScope = scope,
                    timeProvider = timeProvider
                ),
                recordPrivateCalls = true
            )
            messagesVM.setLocation(mockLocation)

            messagesVM to nearbyVM
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun connection() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val testScope = this
        val timeProvider = TestTimeProvider(testScheduler)

        devices = createViewModels(dispatcher, testScope, timeProvider)

        val connectionStates = mutableMapOf<Uuid, MutableList<NearbyDevicesViewModel.ConnectionState>>()
        val jobs = mutableListOf<Job>()
        val latch = MutableStateFlow(0)

        devices.forEach { device ->
            jobs.add(
                backgroundScope.launch(dispatcher) {
                    device.second.connectionFlow
                        .filter { it == NearbyDevicesViewModel.ConnectionState.CONNECTED }
                        .take(1)
                        .collect { state ->
                            Log.i("Connection", "$state")
                            connectionStates.getOrPut(device.second.deviceId) { mutableListOf() }.add(state)
                            latch.value += 1
                        }
                }
            )
        }

        devices.forEach { it.second.startCollektiveProgram() }

        latch.filter { it == deviceCount }.first()

        devices.forEach {
            it.second.setOnlineStatus(false)
            it.second.cancel()
        }
        jobs.forEach { it.cancel() }

        devices.forEach { device ->
            val latest = connectionStates[device.second.deviceId]?.last()
            assertNotNull(latest)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `found neighbor`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val testScope = this
        val timeProvider = TestTimeProvider(testScheduler)

        devices = createViewModels(dispatcher, testScope, timeProvider)

        val neighborhoods = mutableMapOf<Uuid, MutableList<Set<Uuid>>>()
        val jobs = mutableListOf<Job>()
        val latch = MutableStateFlow(0)

        devices.forEach { device ->
            jobs.add(
                backgroundScope.launch(dispatcher) {
                    device.second.dataFlow
                        .filter { it.size == deviceCount - 1 }
                        .take(1)
                        .collect { neighbors ->
                            Log.i("Neighborhood", "$neighbors")
                            neighborhoods.getOrPut(device.second.deviceId) { mutableListOf() }.add(neighbors)
                            latch.value += 1
                        }
                }
            )
        }

        devices.forEach { it.second.startCollektiveProgram() }

        latch.filter { it == deviceCount }.first()

        devices.forEach {
            it.second.setOnlineStatus(false)
            it.second.cancel()
        }
        jobs.forEach { it.cancel() }

        neighborhoods.forEach { (id, neighborList) ->
            val latest = neighborList.last()
            assertNotNull(latest)
            Log.i("Device $id", "sees ${latest.size} neighbors")
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `dynamic movement and message propagation`() = runTest {
        val durationInSeconds = durationOfSimulatedChat * 60
        val dispatcher = StandardTestDispatcher(testScheduler)
        val testScope = this
        val timeProvider = TestTimeProvider(testScheduler)

        devices = createViewModels(dispatcher, testScope, timeProvider)

        val messages = mutableMapOf<Uuid, MutableList<List<Message>>>()
        val jobs = mutableListOf<Job>()

        devices.forEach { (messagesVM, nearbyVM) ->
            jobs += backgroundScope.launch(dispatcher) {
                messagesVM.messages
                    .filter { it.isNotEmpty() }
                    .take(durationInSeconds)
                    .collect { state ->
                        Log.i("Device ${nearbyVM.deviceId}", "Received: $state")
                        messages.getOrPut(nearbyVM.deviceId) { mutableListOf() }.add(state)
                    }
            }

            // simulated movement
            launch(dispatcher) {
                var currentOffset = 0.0
                val steps = durationInSeconds / 5
                repeat(steps) {
                    val newLocation = generateLocationAtDistance(baseLat, baseLon, currentOffset, timeProvider)
                    messagesVM.setLocation(newLocation)
                    currentOffset += 10.0
                    delay(5.seconds)
                }
            }

            // sent messages
            launch(dispatcher) {
                val localId = nearbyVM.deviceId.toString()
                var sentCounter = 0
                repeat(durationInSeconds) {
                    val now = timeProvider.currentTimeMillis()
                    val wasSender = messagesVM.getSendFlag()

                    val shouldBecomeSource = isSource()

                    when {
                        shouldBecomeSource -> {
                            sentCounter++
                            messagesVM.markAsSource(now)
                            val msg = "Hello! I'm device $localId attempt $sentCounter"
                            val dist = Random.nextInt(5000, 10000).toFloat()
                            messagesVM.enqueueMessage(
                                message = msg,
                                time = timeProvider.now(),
                                distance = dist,
                                spreadingTime = Random.nextInt(5, 60)
                            )
                            messagesVM.setSendFlag(true)
                        }
                        wasSender && messagesVM.sourceSince.value?.let { now - it < 2_000 } == true -> {
                            // is source
                        }
                        else -> {
                            messagesVM.clearSourceStatus()
                        }
                    }
                    delay(1.seconds)
                }
            }

            messagesVM.setOnlineStatus(true)
            messagesVM.listenAndSend(nearbyVM, nearbyVM.userName.value)
        }

        advanceTimeBy(durationInSeconds.seconds)

        devices.forEach { device ->
            val received = messages[device.second.deviceId]?.last()
            assertNotNull(received)
        }

        devices.forEach {
            it.first.setOnlineStatus(false)
            it.first.cancel()
        }
        jobs.forEach { it.cancel() }
    }

    fun isSource() = Random.nextFloat() < 0.25f

    private fun randomLocationNearby(
        baseLat: Double,
        baseLon: Double,
        timeProvider: TestTimeProvider,
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
        location.time = timeProvider.currentTimeMillis()
        return location
    }

    private fun generateLocationAtDistance(
        baseLat: Double,
        baseLon: Double,
        distanceMeters: Double,
        timeProvider: TestTimeProvider,
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
        location.time = timeProvider.currentTimeMillis()
        return location
    }

    private fun latLonAltToECEF(lat: Double, lon: Double, alt: Double): Point3D {
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

    private fun ecefToLatLonAlt(point: Point3D): Triple<Double, Double, Double> {
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
