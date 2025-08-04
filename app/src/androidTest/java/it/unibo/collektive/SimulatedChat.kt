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
        val maxLatOffset = maxDistanceMeters / 111_000.0
        val maxLonOffset = maxDistanceMeters / (111_000.0 * cos(Math.toRadians(baseLat)))
        location.latitude = baseLat + Random.nextDouble(-maxLatOffset, maxLatOffset)
        location.longitude = baseLon + Random.nextDouble(-maxLonOffset, maxLonOffset)
        location.altitude = Random.nextDouble(0.0, 100.0)
        location.accuracy = Random.nextFloat() * 5
        location.time = System.currentTimeMillis()
        return location
    }

    @After
    fun tearDown() {
        devices.forEach {
            it.third.cancel()
        }
    }

}
