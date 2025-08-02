package it.unibo.collektive

import android.location.Location
import io.mockk.*
import it.nicolasfarabegoli.mktt.MkttClient
import it.nicolasfarabegoli.mktt.MqttMessage
import it.nicolasfarabegoli.mktt.MqttQoS
import it.unibo.collektive.model.EnqueueMessage
import it.unibo.collektive.viewmodels.MessagesViewModel
import it.unibo.collektive.viewmodels.NearbyDevicesViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

class SpreadingTimeTest {
    private lateinit var mqttClientMock: MkttClient
    private lateinit var messageFlow: MutableSharedFlow<MqttMessage>
    private lateinit var messagesViewModel: MessagesViewModel
    private lateinit var nearbyDevicesViewModel: NearbyDevicesViewModel
    private val activeMessages = mutableMapOf<String, Long>()

    /**
     * Prepares the test environment before each test case.
     *
     * This setup includes:
     * - Creating a relaxed mock of the MQTT client to simulate network behavior.
     * - Initializing a `MutableSharedFlow` to simulate incoming MQTT messages.
     * - Mocking the `NearbyDevicesViewModel` with a fake device ID and username.
     * - Spying on `MessagesViewModel` to allow interaction and inspection.
     * - Defining the behavior of `publish()` to emit messages only if they have not expired,
     *   simulating a message spreading duration in a network.
     *
     * This method is annotated with `@Before`, meaning it runs before each `@Test`.
     */
    @Before
    fun setup() = runTest {
        val mockLocation = randomLocation()
        mqttClientMock = mockk(relaxed = true)
        nearbyDevicesViewModel = mockk(relaxed = true)
        every { nearbyDevicesViewModel.deviceId } returns Uuid.random()
        every { nearbyDevicesViewModel.userName } returns MutableStateFlow("User")
        messageFlow = MutableSharedFlow()
        coEvery {
            mqttClientMock.subscribe(any(), MqttQoS.AtMostOnce)
        } returns messageFlow
        coEvery {
            mqttClientMock.publish(any(), any())
        } coAnswers {
            val topic = firstArg<String>()
            val payload = secondArg<ByteArray>()
            val messageId = String(payload)
            val now = System.currentTimeMillis()
            val spreadingTimeMillis = 5000.toLong()
            activeMessages[messageId] = now + spreadingTimeMillis
            launch {
                delay(1.seconds)
                val currentTime = System.currentTimeMillis()
                if ((activeMessages[messageId] ?: 0) > currentTime) {
                    messageFlow.emit(MqttMessage(topic, payload, MqttQoS.AtLeastOnce, false))
                }
            }
        }
        messagesViewModel = spyk(MessagesViewModel(), recordPrivateCalls = true)
        messagesViewModel.setLocation(mockLocation)
    }

    @Test
    fun `message expires after spreading time`() = runTest {
        messagesViewModel.listenAndSend(nearbyDevicesViewModel)
        val messageText = "Test message"
        val spreadingTime = 5
        val distance = 2000f
        val time = LocalDateTime.now()
        val message = EnqueueMessage(messageText, time, distance, spreadingTime)
        messagesViewModel.enqueueMessage(
            message = messageText,
            time = time,
            distance = distance,
            spreadingTime = spreadingTime
        )
        assertTrue(messagesViewModel.pendingMessages.contains(message))
        messagesViewModel.setSendFlag(flag = true)
        withContext(Dispatchers.Default) {
            delay(1.seconds)
        }
        assertFalse(messagesViewModel.pendingMessages.contains(message))
        withContext(Dispatchers.Default) {
            delay((spreadingTime + 1).seconds)
        }
        // TODO: check if message isn't in the network
    }

    private fun randomLocation(provider: String = "mock"): Location {
        val location = Location(provider)
        location.latitude = Random.nextDouble(-90.0, 90.0)
        location.longitude = Random.nextDouble(-180.0, 180.0)
        location.altitude = Random.nextDouble(0.0, 1000.0)
        location.accuracy = Random.nextFloat() * 10
        location.time = System.currentTimeMillis()
        return location
    }
}
