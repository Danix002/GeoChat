package it.unibo.collektive

import FakeMailbox
import android.util.Log
import io.mockk.every
import io.mockk.spyk
import it.unibo.collektive.utils.CoordinatesGenerator
import it.unibo.collektive.utils.TestParams
import it.unibo.collektive.utils.TestTimeProvider
import it.unibo.collektive.viewmodels.MessagesViewModel
import it.unibo.collektive.viewmodels.NearbyDevicesViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

class SimulatedChatTest {
    private val deviceCount = 5
    private val baseLat = 45.0
    private val baseLon = 7.0
    private val locationGenerator = CoordinatesGenerator()

    private val initialOffsets = listOf(0.0, 100.0, 200.0, 300.0, 400.0)

    private val newOffsets = listOf(0.0, 800.0, 100.0, 3000.0, 1500.0)

    private lateinit var devices: List<Triple<MessagesViewModel, NearbyDevicesViewModel, TestParams>>

    private fun createViewModels(
        dispatcher: CoroutineDispatcher,
        scope: CoroutineScope,
        timeProvider: TestTimeProvider
    ): List<Triple<MessagesViewModel, NearbyDevicesViewModel, TestParams>> {
        return List(deviceCount) { i ->
            val mockLocation = locationGenerator.generateLocationAtDistance(
                baseLat,
                baseLon,
                initialOffsets[i],
                timeProvider
            )
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
                TestParams(0, 0, i.toDouble(), 0)
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
                var shouldBecomeSource = false

                if (params.currentStep % STEP_BEFORE_SIMULATED_MOVEMENT == 0 && params.currentStep != 0) {
                    val newLocation =
                        locationGenerator.generateLocationAtDistance(
                            baseLat,
                            baseLon,
                            newOffsets[params.currentOffset.toInt()],
                            timeProvider
                        )
                    messagesVM.setLocation(newLocation)
                }

                if (params.currentStep % STEP_BEFORE_BE_SOURCE == 0) {
                    shouldBecomeSource = true
                }

                when {
                    shouldBecomeSource -> {
                        params.sentCounter++
                        val msg = "Hello! I'm device $localId attempt ${params.sentCounter}"
                        messagesVM.enqueueMessage(
                            message = msg,
                            time = timeProvider.now(),
                            distance = 1000f,
                            spreadingTime = spreadingTime
                        )
                        messagesVM.setSendFlag(true)
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

    companion object {
        // The duration of the test under this configuration is approximately 5 seconds.
        const val STEP_BEFORE_SIMULATED_MOVEMENT = 5
        const val STEP_BEFORE_BE_SOURCE = 4
        const val DURATION_IN_SECONDS = 9
    }
}
