package it.unibo.collektive.network.mqtt

import io.github.oshai.kotlinlogging.KotlinLogging
import it.nicolasfarabegoli.mktt.MkttClient
import it.unibo.collektive.network.AbstractSerializerMailbox
import it.unibo.collektive.networking.Message
import it.unibo.collektive.networking.SerializedMessage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialFormat
import kotlinx.serialization.json.Json
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class MqttMailbox<ID : Any> private constructor(
    private val deviceId: ID,
    host: String,
    port: Int,
    serializer: SerialFormat,
    retentionTime: Duration,
    private val dispatcher: CoroutineDispatcher,
) : AbstractSerializerMailbox<ID>(deviceId, serializer, retentionTime) {
    private val internalScope = CoroutineScope(dispatcher)
    private val mqttClient = MkttClient(dispatcher) {
        brokerUrl = host
        this.port = port
    }
    private val logger = KotlinLogging.logger("${MqttMailbox::class.simpleName!!}@$deviceId")

    private suspend fun initializeMqttClient() {
        logger.info { "Connecting to the broker..." }
        mqttClient.connect()
        logger.info { "Connection succeeded" }
        internalScope.launch(dispatcher) { receiveHeartbeatPulse() }
        internalScope.launch(dispatcher) { sendHeartbeatPulse() }
        receiveNeighborMessages()
    }

    private suspend fun sendHeartbeatPulse() {
        mqttClient.publish("heartbeat/$deviceId", byteArrayOf())
        delay(1.seconds)
        sendHeartbeatPulse()
    }

    private suspend fun receiveHeartbeatPulse() {
        mqttClient.subscribe("heartbeat/$deviceId").collect {
            logger.info { "Received heartbeat pulse from $deviceId" }
            TODO("Not yet implemented")
        }
    }

    private suspend fun receiveNeighborMessages() {
        TODO()
    }

    override suspend fun close() {
        internalScope.cancel()
        mqttClient.disconnect()
        logger.info { "Disconnected from the broker" }
    }

    override fun onDeliverableReceived(receiverId: ID, message: Message<ID, Any?>) {
        require(message is SerializedMessage<ID>)
    }

    companion object {
        suspend operator fun invoke(
            deviceId: Int,
            host: String,
            port: Int = 1883,
            serializer: SerialFormat = Json,
            retentionTime: Duration = 5.seconds,
            dispatcher: CoroutineDispatcher
        ): MqttMailbox<Int> = coroutineScope {
            MqttMailbox(deviceId, host, port, serializer, retentionTime, dispatcher).apply {
                initializeMqttClient()
            }
        }
    }
}
