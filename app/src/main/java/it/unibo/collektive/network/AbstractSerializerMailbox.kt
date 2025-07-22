package it.unibo.collektive.network

import it.unibo.collektive.aggregate.api.DataSharingMethod
import it.unibo.collektive.aggregate.api.Serialize
import it.unibo.collektive.networking.Mailbox
import it.unibo.collektive.networking.Message
import it.unibo.collektive.networking.NeighborsData
import it.unibo.collektive.networking.OutboundEnvelope
import it.unibo.collektive.networking.SerializedMessage
import it.unibo.collektive.networking.SerializedMessageFactory
import it.unibo.collektive.path.Path
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.BinaryFormat
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialFormat
import kotlinx.serialization.StringFormat
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.encodeToString
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration

/**
 * An abstract mailbox that serializes messages before sending them to the network.
 */
abstract class AbstractSerializerMailbox<ID : Any>(
    private val deviceId: ID,
    private val serializer: SerialFormat,
    private val retentionTime: Duration,
) : Mailbox<ID> {
    protected data class TimedMessage<ID : Any>(val message: Message<ID, Any?>, val timestamp: Instant)

    private val messages = mutableMapOf<ID, TimedMessage<ID>>()
    /**
     * Thread-safe neighbor data structure, implemented as ConcurrentHashMap
     * that maps each deviceId to the timestamp of the last heartbeat received.
     */
    private val neighbors = ConcurrentHashMap<ID, Instant>()
    private val factory = object : SerializedMessageFactory<ID, Any?>(serializer) {}
    private val neighborMessageFlow = MutableSharedFlow<Message<ID, Any?>>()

    /**
     * Typically, a network-based mailbox provides a way to gracefully close the connection.
     * This method should be called when the mailbox is no longer needed.
     */
    abstract suspend fun close()

    /**
     * This method is called when a message is ready to be sent to the network.
     */
    abstract fun onDeliverableReceived(receiverId: ID, message: Message<ID, Any?>)

    /**
     * Add the [deviceId] to the list of neighbors.
     */
    fun addNeighbor(deviceId: ID) {
        neighbors[deviceId] = Clock.System.now()
    }

    /**
     * Remove neighbors that have not sent a heartbeat in a while.
     */
    fun cleanupNeighbors(neighborRetention: Duration) {
        val now = Clock.System.now()
        neighbors.entries.removeIf { (_, timestamp) -> now - timestamp > neighborRetention }
    }

    /**
     * Returns the list of neighbors.
     */
    fun neighbors(): Set<ID> = neighbors.keys

    final override val inMemory: Boolean
        get() = false

    final override fun deliverableFor(outboundMessage: OutboundEnvelope<ID>) {
        for (neighbor in neighbors() - deviceId) {
            val message = outboundMessage.prepareMessageFor(neighbor, factory)
            onDeliverableReceived(neighbor, message)
        }
    }

    final override fun deliverableReceived(message: Message<ID, *>) {
        messages[message.senderId] = TimedMessage(message, Clock.System.now())
        neighborMessageFlow.tryEmit(message)
    }

    final override fun currentInbound(): NeighborsData<ID> = object : NeighborsData<ID> {
        // First, remove all messages that are older than the retention time
        init {
            val nowInstant = Clock.System.now()
            val candidates =
                messages
                    .values
                    .filter { it.timestamp < nowInstant - retentionTime }
            messages.values.removeAll(candidates.toSet())
        }

        override val neighbors: Set<ID> get() = messages.keys

        override fun <Value> dataAt(path: Path, dataSharingMethod: DataSharingMethod<Value>): Map<ID, Value> {
            require(dataSharingMethod is Serialize<Value>) {
                "Serialization has been required for in-memory messages. This is likely a misconfiguration."
            }
            return messages
                .mapValues { (_, timedMessage) ->
                    require(timedMessage.message.sharedData.all { it.value is ByteArray }) {
                        "Message ${timedMessage.message.senderId} is not serialized"
                    }
                    timedMessage.message.sharedData.getOrElse(path) { NoValue }
                }.filterValues { it != NoValue }
                .mapValues { (_, payload) ->
                    val byteArrayPayload = payload as ByteArray
                    serializer.decode(dataSharingMethod.serializer, byteArrayPayload)
                }
        }
    }

    private object NoValue

    /**
     * A helper method to serialize and deserialize messages.
     */
    companion object {
        /**
         * Decode a [value] from a [ByteArray] using the [kSerializer].
         */
        fun <Value> SerialFormat.decode(kSerializer: KSerializer<Value>, value: ByteArray): Value = when (this) {
            is StringFormat -> decodeFromString(kSerializer, value.decodeToString())
            is BinaryFormat -> decodeFromByteArray(kSerializer, value)
            else -> error("Unsupported format: $this")
        }

        /**
         * Encode a [value] to a [ByteArray] using the [kSerializer].
         */
        fun <Value> SerialFormat.encode(kSerializer: KSerializer<Value>, value: Value): ByteArray = when (this) {
            is StringFormat -> encodeToString(kSerializer, value).encodeToByteArray()
            is BinaryFormat -> encodeToByteArray(kSerializer, value)
            else -> error("Unsupported format: $this")
        }

        /**
         * Decode a [value] from a [ByteArray].
         */
        inline fun <reified ID : Any> SerialFormat.decodeSerialMessage(value: ByteArray): SerializedMessage<ID> =
            when (this) {
                is StringFormat -> decodeFromString(value.decodeToString())
                is BinaryFormat -> decodeFromByteArray(value)
                else -> error("Unsupported format: $this")
            }

        /**
         * Encode a [value] to a [ByteArray].
         */
        inline fun <reified ID : Any> SerialFormat.encodeSerialMessage(value: SerializedMessage<ID>): ByteArray =
            when (this) {
                is StringFormat -> encodeToString(value).encodeToByteArray()
                is BinaryFormat -> encodeToByteArray(value)
                else -> error("Unsupported format: $this")
            }
    }
}
