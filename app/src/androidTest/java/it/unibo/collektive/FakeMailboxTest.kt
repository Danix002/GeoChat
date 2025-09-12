package it.unibo.collektive

import FakeMailbox
import android.location.Location
import android.util.Log
import io.mockk.every
import io.mockk.spyk
import it.unibo.collektive.stdlib.util.Point3D
import it.unibo.collektive.utils.ECEFCoordinatesGenerator
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

class FakeMailboxTest {
    private val baseLat = 45.0
    private val baseLon = 7.0
    private val offsets: Map<String, Double> = mapOf("Alice" to 0.0, "Bob" to 100.0)

    private fun createViewModels(
        dispatcher: CoroutineDispatcher,
        scope: CoroutineScope,
        timeProvider: TestTimeProvider,
        userName: String
    ): Pair<MessagesViewModel, NearbyDevicesViewModel> {
        val mockLocation = offsets[userName]?.let {
            generateLocationAtDistance(
                baseLat,
                baseLon,
                it,
                timeProvider)
        }
        val nearbyVM = spyk(
            NearbyDevicesViewModel(
                dispatcher = dispatcher,
                providedScope = scope,
            ),
            recordPrivateCalls = true
        )
        every { nearbyVM.userName } returns MutableStateFlow(userName)

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

        return messagesVM to nearbyVM
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `message delivery via fake brokers`() = runTest {
        FakeBroker.clear()

        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = this
        val timeProvider = TestTimeProvider(testScheduler)

        val deviceA = createViewModels(
            dispatcher = dispatcher,
            scope = scope,
            timeProvider = timeProvider,
            userName = "Alice"
        )

        val deviceB = createViewModels(
            dispatcher = dispatcher,
            scope = scope,
            timeProvider = timeProvider,
            userName = "Bob"
        )

        deviceA.first.setOnlineStatus(true)
        deviceB.first.setOnlineStatus(true)

        deviceA.first.listenAndSend(deviceA.second, "Alice")
        deviceB.first.listenAndSend(deviceB.second, "Bob")

        advanceTimeBy(1.seconds)

        deviceA.first.enqueueMessage(
            "Hello Bob!",
            timeProvider.now(),
            2000f,
            5
        )
        deviceA.first.setSendFlag(true)

        advanceTimeBy(6.seconds)

        val fakeMailboxB = FakeBroker.subscribers[deviceB.second.deviceId]
        fakeMailboxB?.let {
            assertTrue(fakeMailboxB.receivedMessages.isNotEmpty())
        }?: error("No messages received by Bob")

        val deviceBMessages = deviceB.first.getCurrentListOfMessages()
        Log.i("📨 Bob received", deviceBMessages.toString())

        deviceA.first.setOnlineStatus(false)
        deviceB.first.setOnlineStatus(false)
        deviceA.first.cancel()
        deviceB.first.cancel()
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
}
