package it.unibo.collektive

import FakeMailbox
import android.util.Log
import io.mockk.every
import io.mockk.spyk
import it.unibo.collektive.model.EnqueueMessage
import it.unibo.collektive.model.Message
import it.unibo.collektive.utils.CoordinatesGenerator
import it.unibo.collektive.utils.TestTimeProvider
import it.unibo.collektive.viewmodels.MessagesViewModel
import it.unibo.collektive.viewmodels.NearbyDevicesViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
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
    private val locationGenerator = CoordinatesGenerator()
    private val offsets = listOf(0.0, 100.0, 200.0, 300.0, 400.0)
    private lateinit var devices: List<Pair<MessagesViewModel, NearbyDevicesViewModel>>
    private val expectedDistances = listOf(71f, 141f, 211f, 281f)

    private fun createViewModels(
        dispatcher: CoroutineDispatcher,
        scope: CoroutineScope,
        timeProvider: TestTimeProvider
    ): List<Pair<MessagesViewModel, NearbyDevicesViewModel>> {
        return List(deviceCount) { i ->
            val mockLocation = locationGenerator.generateLocationAtDistance(baseLat, baseLon, offsets[i], timeProvider)
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

        devices.drop(1).forEachIndexed { index, device ->
            val received = messages[device.second.deviceId]?.last()
            received?.let {
                assertTrue(received.size == 1)
                assertEquals(
                    expectedDistances[index],
                    received.first().distance
                )
            }?: error("No messages received by ${device.second.deviceId}")
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
}
