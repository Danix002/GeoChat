package it.unibo.collektive

import io.mockk.coEvery
import io.mockk.mockk
import it.nicolasfarabegoli.mktt.MkttClient
import it.nicolasfarabegoli.mktt.MqttMessage
import it.nicolasfarabegoli.mktt.MqttQoS
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

class SimulatedChat {
    // TODO: add fake messages, add fake devices
    private lateinit var mqttClientMock: MkttClient
    private lateinit var messageFlow: MutableSharedFlow<MqttMessage>
    private val activeMessages = mutableMapOf<String, Long>()

    @Before
    fun setup() = runTest {
        // TODO: viewModels mock
        /**Note: understand how to instantiate Collektive without mock*/

        // Network mock
        mqttClientMock = mockk(relaxed = true)
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
            val spreadingTimeMillis = 5000L
            activeMessages[messageId] = now + spreadingTimeMillis
            launch {
                delay(1.seconds)
                val currentTime = System.currentTimeMillis()
                if ((activeMessages[messageId] ?: 0) > currentTime) {
                    messageFlow.emit(MqttMessage(topic, payload, MqttQoS.AtLeastOnce, false))
                }
            }
        }
    }

    @Test
    fun `real chat simulation`() = runTest {
        TODO()
    }

}
