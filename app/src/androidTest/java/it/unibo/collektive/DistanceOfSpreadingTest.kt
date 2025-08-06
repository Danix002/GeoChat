package it.unibo.collektive

import android.location.Location
import android.util.Log
import io.mockk.every
import io.mockk.spyk
import it.nicolasfarabegoli.mktt.MqttMessage
import it.unibo.collektive.model.EnqueueMessage
import it.unibo.collektive.model.Message
import it.unibo.collektive.stdlib.util.Point3D
import it.unibo.collektive.viewmodels.MessagesViewModel
import it.unibo.collektive.viewmodels.NearbyDevicesViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

class DistanceOfSpreadingTest {
    private val deviceCount = 5
    private val baseLat = 45.0
    private val baseLon = 7.0
    private val offsets = listOf(0.0, 100.0, 200.0, 300.0, 400.0)
    private lateinit var devices: List<Triple<MessagesViewModel, NearbyDevicesViewModel, CoroutineScope>>
    private lateinit var messageFlow: MutableSharedFlow<MqttMessage>
    private val expectedDistances = listOf(71f, 141f, 211f, 281f)

    @Before
    fun setup() = runBlocking {
        messageFlow = MutableSharedFlow(extraBufferCapacity = 100)
        devices = List(deviceCount) { i ->
            val mockLocation = generateLocationAtDistance(baseLat, baseLon, offsets[i])
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

    /**
     * Tests that message is sent with a specific distance.
     *
     * ⚠️ **Note**: This test is sensitive to coroutine scheduling and wall-clock timing.
     * When running the full test suite, this test may occasionally fail due to timing interleavings
     * caused by concurrent tests or system load. If you encounter a failure here,
     * try running this test **in isolation**.
     *
     * This is expected behavior under certain conditions and does not necessarily indicate a bug.
     */
    @Test
    fun `distance value correspondence`() = runBlocking {
        val messages = mutableMapOf<Uuid, MutableList<List<Message>>>()
        val jobs = devices.map { device ->
            launch {
                device.first.messages.collect { state ->
                    Log.i("Current list of all received messages", "$state")
                    messages.getOrPut(device.second.deviceId) { mutableListOf() }.add(state)
                }
            }
        }
        devices.forEach { viewModels ->
            viewModels.first.setOnlineStatus(flag = true)
            viewModels.first.listenAndSend(viewModels.second, viewModels.second.userName.value)
        }
        delay(10.seconds)
        val senderMessagesVM = devices[0].first
        val senderNearbyVM = devices[0].second
        val message = EnqueueMessage(
            text = "Message by ${senderNearbyVM.deviceId}",
            time = LocalDateTime.now(),
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
        delay(10.seconds)
        devices.drop(1).forEachIndexed { index, device ->
            val received = messages[device.second.deviceId]?.lastOrNull()
            assertTrue(received!!.size == 1)
            assertNotNull(received)
            assertEquals(
                expectedDistances[index],
                received.first().distance
            )
        }
        jobs.forEach { it.cancel() }
    }

    /**
     * Tests that message was not sent with a specific distance.
     *
     * ⚠️ **Note**: This test is sensitive to coroutine scheduling and wall-clock timing.
     * When running the full test suite, this test may occasionally fail due to timing interleavings
     * caused by concurrent tests or system load. If you encounter a failure here,
     * try running this test **in isolation**.
     *
     * This is expected behavior under certain conditions and does not necessarily indicate a bug.
     */
    @Test
    fun `message not received because the devices is not in range`() = runBlocking {
        val messages = mutableMapOf<Uuid, MutableList<List<Message>>>()
        val jobs = devices.map { device ->
            launch {
                device.first.messages.collect { state ->
                    Log.i("Current list of all received messages", "$state")
                    messages.getOrPut(device.second.deviceId) { mutableListOf() }.add(state)
                }
            }
        }
        devices.forEach { viewModels ->
            viewModels.first.setOnlineStatus(flag = true)
            viewModels.first.listenAndSend(viewModels.second, viewModels.second.userName.value)
        }
        delay(10.seconds)
        val senderMessagesVM = devices[0].first
        val senderNearbyVM = devices[0].second
        val message = EnqueueMessage(
            text = "Message by ${senderNearbyVM.deviceId}",
            time = LocalDateTime.now(),
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
        delay(10.seconds)
        devices.drop(1).forEachIndexed { index, device ->
            val received = messages[device.second.deviceId]?.lastOrNull()
            if(index == 0) {
                assertTrue(received!!.isNotEmpty())
            }else{
                assertFalse(received!!.isNotEmpty())
            }
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
