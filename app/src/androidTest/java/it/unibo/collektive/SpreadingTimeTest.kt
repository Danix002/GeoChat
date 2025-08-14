package it.unibo.collektive

import android.location.Location
import android.util.Log
import io.mockk.*
import it.unibo.collektive.model.EnqueueMessage
import it.unibo.collektive.utils.TestTimeProvider
import it.unibo.collektive.viewmodels.MessagesViewModel
import it.unibo.collektive.viewmodels.NearbyDevicesViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

class SpreadingTimeTest {
    private lateinit var messagesViewModel: MessagesViewModel
    private lateinit var nearbyDevicesViewModel: NearbyDevicesViewModel
    private val messagesValue = mutableMapOf<Collektive<Uuid, Unit>, String>()

    private fun createViewModels(
        dispatcher: CoroutineDispatcher,
        scope: CoroutineScope,
        timeProvider: TestTimeProvider
    ): Pair<MessagesViewModel, NearbyDevicesViewModel> {
        val mockLocation = randomLocation()

        val nearbyVM = mockk<NearbyDevicesViewModel>(relaxed = true)
        every { nearbyVM.deviceId } returns Uuid.random()
        every { nearbyVM.userName } returns MutableStateFlow("User")

        val messagesVM = spyk(
            MessagesViewModel(
                dispatcher = dispatcher,
                providedScope = scope,
                timeProvider = timeProvider
            ),
            recordPrivateCalls = true
        )

        coEvery {
            messagesVM.createProgram(any(), any(), any())
        } answers {
            val enqueueMessage = thirdArg<EnqueueMessage>()
            val program = mockk<Collektive<Uuid, Unit>>(relaxed = true)
            messagesValue[program] = enqueueMessage.text
            program
        }

        messagesVM.setLocation(mockLocation)

        return messagesVM to nearbyVM
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `message expires after spreading time`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val testScope = this
        val timeProvider = TestTimeProvider(testScheduler)

        val (msgVM, nearVM) = createViewModels(dispatcher, testScope, timeProvider)
        messagesViewModel = msgVM
        nearbyDevicesViewModel = nearVM

        val emissions = mutableListOf<List<Pair<Collektive<Uuid, Unit>, Long>>>()

        val job = backgroundScope.launch(dispatcher) {
            messagesViewModel.programs.collect {
                Log.i("Programs size", it.size.toString())
                Log.i("Programs value", it.toString())
                emissions.add(it)
            }
        }

        messagesViewModel.setOnlineStatus(true)
        messagesViewModel.listenAndSend(nearbyDevicesViewModel, nearbyDevicesViewModel.userName.value)

        val message = EnqueueMessage(
            text = "Test message",
            time = timeProvider.now(),
            distance = 2000f,
            spreadingTime = 5
        )

        messagesViewModel.enqueueMessage(
            message = message.text,
            time = message.time,
            distance = message.distance,
            spreadingTime = message.spreadingTime
        )

        assertTrue(messagesViewModel.pendingMessages.contains(message))
        messagesViewModel.setSendFlag(true)

        advanceTimeBy(1.seconds)
        assertFalse(messagesViewModel.pendingMessages.contains(message))

        advanceTimeBy(1.seconds)
        assertEquals(2, emissions.last().size) // Listener + Sender

        advanceTimeBy((message.spreadingTime + 1) * 1_000L)
        assertEquals(1, emissions.last().size) // Only listener

        messagesViewModel.setSendFlag(false)
        messagesViewModel.setOnlineStatus(false)
        messagesViewModel.cancel()
        job.cancel()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `messages sent in order`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val testScope = this
        val timeProvider = TestTimeProvider(testScheduler)

        val (msgVM, nearVM) = createViewModels(dispatcher, testScope, timeProvider)
        messagesViewModel = msgVM
        nearbyDevicesViewModel = nearVM

        val emissions = mutableListOf<List<Pair<Collektive<Uuid, Unit>, Long>>>()

        val job = backgroundScope.launch(dispatcher) {
            messagesViewModel.programs.collect { programs ->
                emissions.add(programs)
            }
        }

        messagesViewModel.setOnlineStatus(flag = true)
        messagesViewModel.listenAndSend(nearbyDevicesViewModel, nearbyDevicesViewModel.userName.value)

        val firstMessage = EnqueueMessage(
            text = "1° Test message",
            time = timeProvider.now(),
            distance = 2000f,
            spreadingTime = 5
        )

        val secondMessage = EnqueueMessage(
            text = "2° Test message",
            time = timeProvider.now(),
            distance = 2000f,
            spreadingTime = 8
        )

        messagesViewModel.enqueueMessage(
            message = firstMessage.text,
            time = firstMessage.time,
            distance = firstMessage.distance,
            spreadingTime = firstMessage.spreadingTime
        )

        messagesViewModel.setSendFlag(flag = true)

        assertTrue(messagesViewModel.pendingMessages.contains(firstMessage))

        advanceTimeBy(1.seconds)

        messagesViewModel.enqueueMessage(
            message = secondMessage.text,
            time = secondMessage.time,
            distance = secondMessage.distance,
            spreadingTime = secondMessage.spreadingTime
        )

        assertTrue(messagesViewModel.pendingMessages.contains(secondMessage))
        assertFalse(messagesViewModel.pendingMessages.contains(firstMessage))

        advanceTimeBy(1.seconds)

        assertFalse(messagesViewModel.pendingMessages.contains(secondMessage))

        var currentSpread = messagesViewModel.programs.value.mapNotNull { (program, _) ->
            messagesValue[program]
        }

        assertEquals("", currentSpread[0]) // Listener
        assertEquals("1° Test message", currentSpread[1])
        assertEquals("2° Test message", currentSpread[2])

        repeat(firstMessage.spreadingTime + 1) {
            advanceTimeBy(1.seconds)
        }

        currentSpread = messagesViewModel.programs.value.mapNotNull { (program, _) ->
            messagesValue[program]
        }

        assertEquals("", currentSpread[0])
        assertEquals("2° Test message", currentSpread[1])

        repeat((secondMessage.spreadingTime - firstMessage.spreadingTime) + 1) {
            advanceTimeBy(1.seconds)
        }

        currentSpread = messagesViewModel.programs.value.mapNotNull { (program, _) ->
            messagesValue[program]
        }

        assertEquals(1, emissions.last().size) // Only listener
        assertEquals("", currentSpread[0])

        messagesViewModel.setSendFlag(false)
        messagesViewModel.setOnlineStatus(false)
        messagesViewModel.cancel()
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
}
