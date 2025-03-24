package it.unibo.collektive.network.mqtt

import io.github.oshai.kotlinlogging.KotlinLogging
import it.nicolasfarabegoli.mktt.MkttClient
import it.nicolasfarabegoli.mktt.MqttQoS
import it.unibo.collektive.network.AbstractSerializerMailbox
import it.unibo.collektive.networking.Message
import it.unibo.collektive.networking.SerializedMessage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.serialization.SerialFormat
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class MqttMailbox private constructor(
    private val deviceId: UUID,
    host: String,
    port: Int,
    private val serializer: SerialFormat,
    private val retentionTime: Duration,
    private val dispatcher: CoroutineDispatcher,
) : AbstractSerializerMailbox<UUID>(deviceId, serializer, retentionTime) {
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
        internalScope.launch { cleanHeartbeatPulse() }
        internalScope.launch(dispatcher) { receiveNeighborMessages() }
    }

    private suspend fun sendHeartbeatPulse() {
        mqttClient.publish(heartbeatTopic(deviceId), byteArrayOf())
        delay(1.seconds)
        sendHeartbeatPulse()
    }

    private suspend fun receiveHeartbeatPulse() {
        mqttClient.subscribe(HEARTBEAT_WILD_CARD).collect {
            logger.info { "Received heartbeat pulse from $deviceId" }
            val deviceId = UUID.fromString(it.topic.split("/").last())
            addNeighbor(deviceId)
        }
    }

    private suspend fun cleanHeartbeatPulse() {
        val toRemove = neighbors.filter { it.timestamp < Clock.System.now() - retentionTime }
        for (neighbor in toRemove) {
            neighbors.remove(neighbor)
            logger.info { "Neighbor $neighbor has been removed" }
        }
        delay(retentionTime)
        cleanHeartbeatPulse()
    }

    private suspend fun receiveNeighborMessages() {
        mqttClient.subscribe(heartbeatTopic(deviceId)).collect {
            try {
                val deserialized = serializer.decodeSerialMessage<UUID>(it.payload)
                logger.debug { "Received message from ${deserialized.senderId}" }
                deliverableReceived(deserialized)
            } catch (exception: SerializationException) {
                logger.error { "Failed to deserialize message from ${it.topic}: ${exception.message}" }
            }
        }
    }

    override suspend fun close() {
        internalScope.cancel()
        mqttClient.disconnect()
        logger.info { "Disconnected from the broker" }
    }

    override fun onDeliverableReceived(receiverId: UUID, message: Message<UUID, Any?>) {
        require(message is SerializedMessage<UUID>)
        internalScope.launch(dispatcher) {
            mqttClient.publish(
                topic = deviceTopic(receiverId),
                qos = MqttQoS.AtLeastOnce,
                message = serializer.encodeSerialMessage(message),
            )
        }
    }

    companion object {
        suspend operator fun invoke(
            deviceId: UUID,
            host: String,
            port: Int = 1883,
            serializer: SerialFormat = Json,
            retentionTime: Duration = 5.seconds,
            dispatcher: CoroutineDispatcher,
        ): MqttMailbox = coroutineScope {
            MqttMailbox(deviceId, host, port, serializer, retentionTime, dispatcher).apply {
                initializeMqttClient()
            }
        }

        private const val APP_NAMESPACE = "CollektiveExampleAndroid"
        private const val HEARTBEAT_WILD_CARD = "$APP_NAMESPACE/heartbeat/+"
        private fun deviceTopic(deviceId: UUID) = "$APP_NAMESPACE/device/$deviceId"
        private fun heartbeatTopic(deviceId: UUID) = "$APP_NAMESPACE/heartbeat/$deviceId"
    }
}
