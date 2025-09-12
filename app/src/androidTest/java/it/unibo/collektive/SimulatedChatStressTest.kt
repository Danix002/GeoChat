package it.unibo.collektive

import FakeMailbox
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
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import kotlin.collections.isNotEmpty
import kotlin.math.cos
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds
import it.unibo.collektive.utils.ECEFCoordinatesGenerator

class SimulatedChatStressTest {
    private val deviceCount = 5
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
                    providedScope = scope,
                ),
                recordPrivateCalls = true
            )
            every { nearbyVM.userName } returns MutableStateFlow("User $i")

            val messagesVM = spyk(
                MessagesViewModel(
                    dispatcher = dispatcher,
                    providedScope = scope,
                    timeProvider = timeProvider,
                    mailboxFactory = { id -> FakeMailbox(id) }
                ),
                recordPrivateCalls = true
            )
            messagesVM.setLocation(mockLocation)

            Triple(
                messagesVM,
                nearbyVM,
                TestParams(0, 0, 0.0, 0)
            )
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `dynamic movement and message propagation`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val testScope = this
        val timeProvider = TestTimeProvider(testScheduler)

        devices = createViewModels(dispatcher, testScope, timeProvider)

        devices.forEach { (messagesVM, nearbyVM) ->
            messagesVM.setOnlineStatus(true)
            messagesVM.listenAndSend(nearbyVM, nearbyVM.userName.value)
        }

        val spreadingTime = 5

        repeat(DURATION_IN_SECONDS) {
            devices.forEach { (messagesVM, nearbyVM, params) ->
                val localId = nearbyVM.deviceId.toString()

                if (params.currentStep % STEP_BEFORE_SIMULATED_MOVEMENT == 0 && params.currentStep != 0) {
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
                var shouldBecomeSource = isSource()

                if(!shouldBecomeSource && params.failedAttempts == MAX_FAILED_ATTEMPTS){
                    shouldBecomeSource = true
                } else if (!shouldBecomeSource && params.failedAttempts < MAX_FAILED_ATTEMPTS) params.failedAttempts++

                when {
                    shouldBecomeSource && !wasSender -> {
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
                        params.failedAttempts = 0
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
            Log.i("${device.second.deviceId}", "Received: ${received.size}")
            Log.i("${device.second.deviceId}", "Received: $received")
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
        val generator = ECEFCoordinatesGenerator()
        val location = Location(provider)
        val baseECEF = generator.latLonAltToECEF(baseLat, baseLon, 0.0)
        val newECEF = Point3D(Triple(baseECEF.x, baseECEF.y, baseECEF.z))
        val (newLat, newLon, newAlt) = generator.ECEFToLatLonAlt(newECEF)
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

    companion object {
        // The duration of the test under this configuration is approximately 25 seconds.
        const val MAX_FAILED_ATTEMPTS = 1
        const val STEP_BEFORE_SIMULATED_MOVEMENT = 5
        const val DURATION_IN_SECONDS = 11
    }
}
