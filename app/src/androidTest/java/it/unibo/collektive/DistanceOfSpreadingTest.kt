package it.unibo.collektive

import android.location.Location
import android.util.Log
import io.mockk.every
import io.mockk.spyk
import it.nicolasfarabegoli.mktt.MqttMessage
import it.unibo.collektive.model.EnqueueMessage
import it.unibo.collektive.model.Message
import it.unibo.collektive.stdlib.util.Point3D
import it.unibo.collektive.utils.TestTimeProvider
import it.unibo.collektive.viewmodels.MessagesViewModel
import it.unibo.collektive.viewmodels.NearbyDevicesViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

class DistanceOfSpreadingTest {
    private val deviceCount = 5
    private val baseLat = 45.0
    private val baseLon = 7.0
    private val offsets = listOf(0.0, 100.0, 200.0, 300.0, 400.0)
    private lateinit var devices: List<Pair<MessagesViewModel, NearbyDevicesViewModel>>
    private val expectedDistances = listOf(71f, 141f, 211f, 281f)

    private fun createViewModels(
        dispatcher: CoroutineDispatcher,
        scope: CoroutineScope,
        timeProvider: TestTimeProvider
    ): List<Pair<MessagesViewModel, NearbyDevicesViewModel>> {
        return List(deviceCount) { i ->
            val mockLocation = generateLocationAtDistance(baseLat, baseLon, offsets[i])
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
    fun `distance value correspondence`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val testScope = this
        val timeProvider = TestTimeProvider(testScheduler)

        devices = createViewModels(dispatcher, testScope, timeProvider)

        val messages = mutableMapOf<Uuid, MutableList<List<Message>>>()
        val jobs = mutableListOf<Job>()

        devices.drop(1).forEach { device ->
            jobs.add(
                backgroundScope.launch(dispatcher) {
                    device.first.messages
                        .filter { it.isNotEmpty() }
                        .take(1)
                        .collect { state ->
                            Log.i("Received", "$state")
                            messages.getOrPut(device.second.deviceId) { mutableListOf() }.add(state)
                        }
                }
            )
        }

        devices.forEach { viewModels ->
            viewModels.first.setOnlineStatus(flag = true)
            viewModels.first.listenAndSend(viewModels.second, viewModels.second.userName.value)
        }

        advanceTimeBy(1.seconds)

        val senderMessagesVM = devices[0].first
        val senderNearbyVM = devices[0].second
        val message = EnqueueMessage(
            text = "Message by ${senderNearbyVM.deviceId}",
            time = timeProvider.now(),
            distance = 2000f,
            spreadingTime = 5
        )
        senderMessagesVM.enqueueMessage(
            message = message.text,
            time = message.time,
            distance = message.distance,
            spreadingTime = message.spreadingTime
        )
        senderMessagesVM.setSendFlag(flag = true)

        advanceTimeBy((message.spreadingTime + 1) * 1_000L)

        senderMessagesVM.setSendFlag(flag = false)

        devices.drop(1).forEachIndexed { index, device ->
            val received = messages[device.second.deviceId]?.last()
            received?.let {
                assertTrue(received.size == 1)
                assertEquals(
                    expectedDistances[index],
                    received.first().distance
                )
            }
        }

        devices.forEach { viewModels ->
            viewModels.first.setOnlineStatus(false)
            viewModels.first.cancel()
        }
        jobs.forEach { it.cancel() }
        jobs.clear()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `message not received because the devices is not in range`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val testScope = this
        val timeProvider = TestTimeProvider(testScheduler)

        devices = createViewModels(dispatcher, testScope, timeProvider)

        val messages = mutableMapOf<Uuid, MutableList<List<Message>>>()
        val jobs = mutableListOf<Job>()

        devices.drop(1).forEach { device ->
            jobs.add(
                backgroundScope.launch(dispatcher) {
                    device.first.messages
                        .filter { it.isNotEmpty() }
                        .take(1)
                        .collect { state ->
                            Log.i("Received", "$state")
                            messages.getOrPut(device.second.deviceId) { mutableListOf() }.add(state)
                        }
                }
            )
        }
        devices.forEach { viewModels ->
            viewModels.first.setOnlineStatus(flag = true)
            viewModels.first.listenAndSend(viewModels.second, viewModels.second.userName.value)
        }

        val senderMessagesVM = devices[0].first
        val senderNearbyVM = devices[0].second
        val message = EnqueueMessage(
            text = "Message by ${senderNearbyVM.deviceId}",
            time = timeProvider.now(),
            distance = 100f,
            spreadingTime = 5
        )
        senderMessagesVM.enqueueMessage(
            message = message.text,
            time = message.time,
            distance = message.distance,
            spreadingTime = message.spreadingTime
        )
        senderMessagesVM.setSendFlag(flag = true)

        advanceTimeBy((message.spreadingTime + 1) * 1_000L)

        senderMessagesVM.setSendFlag(flag = false)

        devices.drop(1).forEachIndexed { index, device ->
            val received = messages[device.second.deviceId]?.last()
            received?.let {
                if(index == 0) {
                    assertTrue(received.isNotEmpty())
                }else{
                    assertFalse(received.isNotEmpty())
                }
            }
        }

        devices.forEach { viewModels ->
            viewModels.first.setOnlineStatus(false)
            viewModels.first.cancel()
        }
        jobs.forEach { it.cancel() }
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
