package it.unibo.collektive

import io.mockk.every
import io.mockk.spyk
import it.unibo.collektive.viewmodels.MessagesViewModel
import it.unibo.collektive.viewmodels.NearbyDevicesViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Test
import android.location.Location
import android.util.Log
import it.unibo.collektive.stdlib.util.Point3D
import it.unibo.collektive.utils.TestParams
import it.unibo.collektive.utils.TestTimeProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import org.junit.Assert.assertEquals
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import kotlin.collections.isNotEmpty
import kotlin.math.cos
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

class SimulatedChat {
    private val deviceCount = 10
    private val baseLat = Random.nextDouble(-90.0, 90.0)
    private val baseLon = Random.nextDouble(-180.0, 180.0)
    private lateinit var devices: List<Triple<MessagesViewModel, NearbyDevicesViewModel, TestParams>>

    private fun createViewModels(
        dispatcher: CoroutineDispatcher,
        scope: CoroutineScope,
        timeProvider: TestTimeProvider
    ): List<Triple<MessagesViewModel, NearbyDevicesViewModel, TestParams>> {
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

            Triple(
                messagesVM,
                nearbyVM,
                TestParams(0, 0, 0.0)
            )
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
        val completionChannel = Channel<Unit>(deviceCount)

        devices.forEach { device ->
            jobs.add(
                backgroundScope.launch(dispatcher) {
                    device.second.connectionFlow
                        .filter { it == NearbyDevicesViewModel.ConnectionState.CONNECTED }
                        .take(1)
                        .collect { state ->
                            Log.i("Connection", "$state")
                            connectionStates.getOrPut(device.second.deviceId) { mutableListOf() }.add(state)
                            completionChannel.send(Unit)
                        }
                }
            )
        }

        devices.forEach { it.second.startCollektiveProgram() }

        devices.forEach { device ->
            val latest = connectionStates[device.second.deviceId]?.lastOrNull()
            latest?.let {
                assertEquals(NearbyDevicesViewModel.ConnectionState.CONNECTED, latest)
            }
        }

        devices.forEach {
            it.first.setOnlineStatus(false)
            it.first.cancel()
            it.second.setOnlineStatus(false)
            it.second.cancel()
        }
        jobs.forEach { it.cancel() }
        jobs.clear()

        Log.i("Finish", "ok")
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
        val completionChannel = Channel<Unit>(deviceCount)

        devices.forEach { device ->
            jobs.add(
                backgroundScope.launch(dispatcher) {
                    device.second.dataFlow
                        .filter { it.size == deviceCount - 1 }
                        .take(1)
                        .collect { neighbors ->
                            Log.i("Neighborhood", "$neighbors")
                            neighborhoods.getOrPut(device.second.deviceId) { mutableListOf() }.add(neighbors)
                            completionChannel.send(Unit)
                        }
                }
            )
        }

        devices.forEach { it.second.startCollektiveProgram() }

        neighborhoods.forEach { (id, neighborList) ->
            val latest = neighborList.lastOrNull()
            latest?.let {
                assertEquals(deviceCount - 1, latest.size)
            }
        }

        devices.forEach {
            it.first.setOnlineStatus(false)
            it.first.cancel()
            it.second.setOnlineStatus(false)
            it.second.cancel()
        }
        jobs.forEach { it.cancel() }
        jobs.clear()

        Log.i("Finish", "ok")
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `dynamic movement and message propagation`() = runTest {
        val durationInSeconds = 16
        val dispatcher = StandardTestDispatcher(testScheduler)
        val testScope = this
        val timeProvider = TestTimeProvider(testScheduler)

        devices = createViewModels(dispatcher, testScope, timeProvider)

        devices.forEach { (messagesVM, nearbyVM) ->
            messagesVM.setOnlineStatus(true)
            messagesVM.listenAndSend(nearbyVM, nearbyVM.userName.value)
        }

        val spreadingTime = 5

        repeat(durationInSeconds) {
            devices.forEach { (messagesVM, nearbyVM, params) ->
                val localId = nearbyVM.deviceId.toString()

                if (params.currentStep % 5 == 0) {
                    val newLocation =
                        generateLocationAtDistance(
                            baseLat,
                            baseLon,
                            params.currentOffset,
                            timeProvider
                        )
                    messagesVM.setLocation(newLocation)
                    params.currentOffset += 10.0
                }

                val now = timeProvider.currentTimeMillis()
                val wasSender = messagesVM.getSendFlag()
                val shouldBecomeSource = isSource()

                when {
                    shouldBecomeSource -> {
                        params.sentCounter++
                        messagesVM.markAsSource(now)
                        val msg = "Hello! I'm device $localId attempt ${params.sentCounter}"
                        val dist = Random.nextInt(5000, 10000).toFloat()
                        messagesVM.enqueueMessage(
                            message = msg,
                            time = timeProvider.now(),
                            distance = dist,
                            spreadingTime = spreadingTime
                        )
                        messagesVM.setSendFlag(true)
                    }

                    wasSender && messagesVM.sourceSince.value?.let { now - it < 2_000 } == true -> {
                        // Nothing
                    }

                    else -> {
                        messagesVM.clearSourceStatus()
                    }
                }

                params.currentStep++
            }
            advanceTimeBy(1.seconds)
        }

        advanceTimeBy((spreadingTime + 1).seconds)

        devices.forEach { device ->
            val received = device.first.getCurrentListOfMessages()
            assertTrue(received.isNotEmpty())
            Log.i("${device.second.deviceId}", "Received size: ${received.size}")
        }

        devices.forEach {
            it.first.setOnlineStatus(false)
            it.first.cancel()
            it.second.setOnlineStatus(false)
            it.second.cancel()
        }

        Log.i("Finish", "ok")
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
