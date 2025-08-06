package it.unibo.collektive

import android.location.Location
import android.util.Log
import io.mockk.*
import it.unibo.collektive.model.EnqueueMessage
import it.unibo.collektive.viewmodels.MessagesViewModel
import it.unibo.collektive.viewmodels.NearbyDevicesViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

class SpreadingTimeTest {
    private lateinit var messagesViewModel: MessagesViewModel
    private lateinit var nearbyDevicesViewModel: NearbyDevicesViewModel
    private val messagesValue = mutableMapOf<Collektive<Uuid, Unit>, String>()
    private lateinit var testScope: CoroutineScope

    @Before
    fun setup() = runBlocking {
        val mockLocation = randomLocation()
        testScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        nearbyDevicesViewModel = mockk(relaxed = true)
        every { nearbyDevicesViewModel.deviceId } returns Uuid.random()
        every { nearbyDevicesViewModel.userName } returns MutableStateFlow("User")

        messagesViewModel = spyk(MessagesViewModel(providedScope = testScope), recordPrivateCalls = true)
        coEvery {
            messagesViewModel.createProgram(any(), any(), any())
        } answers {
            val enqueueMessage = thirdArg<EnqueueMessage>()
            val program = mockk<Collektive<Uuid, Unit>>(relaxed = true)
            messagesValue[program] = enqueueMessage.text
            program
        }
        messagesViewModel.setLocation(mockLocation)
    }

    /**
     * Tests that messages expires after spreading time set by user.
     *
     * ⚠️ **Note**: This test is sensitive to coroutine scheduling and wall-clock timing.
     * When running the full test suite, this test may occasionally fail due to timing interleavings
     * caused by concurrent tests or system load. If you encounter a failure here,
     * try running this test **in isolation**.
     *
     * This is expected behavior under certain conditions and does not necessarily indicate a bug.
     */
    @Test
    fun `message expires after spreading time`() = runBlocking {
        val emissions = mutableListOf<List<Pair<Collektive<Uuid, Unit>, Long>>>()
        val job = launch {
            messagesViewModel.programs
                .collect { programs ->
                    Log.i("Active program", "${programs.size}")
                    emissions.add(programs)
                }
        }
        messagesViewModel.setOnlineStatus(flag = true)
        messagesViewModel.listenAndSend(nearbyDevicesViewModel, nearbyDevicesViewModel.userName.value)
        val message = EnqueueMessage(text = "Test message", time = LocalDateTime.now(), distance = 2000f, spreadingTime = 5)
        messagesViewModel.enqueueMessage(
            message = message.text,
            time = message.time,
            distance = message.distance,
            spreadingTime = message.spreadingTime
        )
        assertTrue(messagesViewModel.pendingMessages.contains(message))
        messagesViewModel.setSendFlag(flag = true)
        delay(1.seconds)
        assertFalse(messagesViewModel.pendingMessages.contains(message))
        delay(1.seconds)
        assertTrue(emissions.last().size == 2) // Listener + Sender of the message
        delay((message.spreadingTime + 1).seconds)
        assertTrue(emissions.last().size == 1) // Only listener
        job.cancel()
    }

    /**
     * Tests that messages are sent in FIFO order based on their enqueue time and spreading duration.
     *
     * ⚠️ **Note**: This test is sensitive to coroutine scheduling and wall-clock timing.
     * When running the full test suite, this test may occasionally fail due to timing interleavings
     * caused by concurrent tests or system load. If you encounter a failure here,
     * try running this test **in isolation**.
     *
     * This is expected behavior under certain conditions and does not necessarily indicate a bug.
     */
    @Test
    fun `messages sent in order`() = runBlocking {
        val emissions = mutableListOf<List<Pair<Collektive<Uuid, Unit>, Long>>>()
        val job = launch {
            messagesViewModel.programs
                .collect { programs ->
                    Log.i("Active program", "${programs.size}")
                    emissions.add(programs)
                }
        }
        messagesViewModel.setOnlineStatus(flag = true)
        messagesViewModel.listenAndSend(nearbyDevicesViewModel, nearbyDevicesViewModel.userName.value)
        val firstMessage = EnqueueMessage(text = "1° Test message", time = LocalDateTime.now(), distance = 2000f, spreadingTime = 5)
        messagesViewModel.enqueueMessage(
            message = firstMessage.text,
            time = firstMessage.time,
            distance = firstMessage.distance,
            spreadingTime = firstMessage.spreadingTime
        )
        val secondMessage = EnqueueMessage(text = "2° Test message", time = LocalDateTime.now(), distance = 2000f, spreadingTime = 8)
        messagesViewModel.enqueueMessage(
            message = secondMessage.text,
            time = secondMessage.time,
            distance = secondMessage.distance,
            spreadingTime = secondMessage.spreadingTime
        )
        assertTrue(messagesViewModel.pendingMessages.contains(firstMessage))
        assertTrue(messagesViewModel.pendingMessages.contains(secondMessage))
        messagesViewModel.setSendFlag(flag = true)
        delay(1.seconds)
        // Testing FIFO queue scheduling
        assertFalse(messagesViewModel.pendingMessages.contains(firstMessage))
        assertTrue(messagesViewModel.pendingMessages.contains(secondMessage))
        delay(1.seconds)
        assertFalse(messagesViewModel.pendingMessages.contains(secondMessage))
        var currentSpread = messagesViewModel.programs.value.mapNotNull { (program, _) ->
            messagesValue[program]
        }
        assertEquals("", currentSpread[0]) // Listener
        assertEquals("1° Test message", currentSpread[1])
        assertEquals("2° Test message", currentSpread[2])
        delay((firstMessage.spreadingTime + 1).seconds)
        currentSpread = messagesViewModel.programs.value.mapNotNull { (program, _) ->
            messagesValue[program]
        }
        assertEquals("", currentSpread[0])
        assertEquals("2° Test message", currentSpread[1])
        delay(((secondMessage.spreadingTime - firstMessage.spreadingTime) + 1).seconds)
        currentSpread = messagesViewModel.programs.value.mapNotNull { (program, _) ->
            messagesValue[program]
        }
        assertTrue(emissions.last().size == 1) // Only listener
        assertEquals("", currentSpread[0])
        job.cancel()
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

    @After
    fun tearDown() {
        testScope.cancel()
    }
}
