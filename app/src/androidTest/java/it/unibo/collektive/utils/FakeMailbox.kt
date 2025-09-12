import it.unibo.collektive.networking.Mailbox
import it.unibo.collektive.networking.Message
import it.unibo.collektive.networking.NeighborsData
import it.unibo.collektive.networking.OutboundEnvelope
import it.unibo.collektive.networking.SerializedMessageFactory
import it.unibo.collektive.path.Path
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlin.uuid.Uuid
import kotlinx.serialization.json.Json
import it.unibo.collektive.aggregate.api.DataSharingMethod
import it.unibo.collektive.aggregate.api.Serialize
import it.unibo.collektive.networking.SerializedMessage
import kotlinx.serialization.json.JsonElement

/**
 * Fake mailbox: broker MQTT in-process.
 */
class FakeMailbox(val deviceId: Uuid) : Mailbox<Uuid> {
    private val _receivedMessages = mutableListOf<Message<Uuid, *>>()
    val receivedMessages: List<Message<Uuid, *>> get() = _receivedMessages

    // Flow for observe incoming messages
    private val _messageFlow = MutableSharedFlow<Message<Uuid, *>>(extraBufferCapacity = 64)
    val messageFlow = _messageFlow.asSharedFlow()

    init {
        FakeBroker.register(deviceId, this)
    }

    override val inMemory: Boolean = false

    override fun deliverableFor(outboundMessage: OutboundEnvelope<Uuid>) {
        FakeBroker.dispatch(deviceId, outboundMessage)
    }

    override fun deliverableReceived(message: Message<Uuid, *>) {
        _receivedMessages.add(message)
        _messageFlow.tryEmit(message)
        if (message is SerializedMessage<*>) {
            val json = Json
            message.sharedData.forEach { (path, payload) ->
                try {
                    val element: JsonElement = json.decodeFromString(String(payload))
                    //println("📩 Device $deviceId received: $element")
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    override fun currentInbound(): NeighborsData<Uuid> {
        return object : NeighborsData<Uuid> {
            override val neighbors: Set<Uuid> = FakeBroker.subscribers.keys - deviceId

            override fun <Value> dataAt(
                path: Path,
                dataSharingMethod: DataSharingMethod<Value>
            ): Map<Uuid, Value> {
                require(dataSharingMethod is Serialize<Value>)
                val json = Json
                return _receivedMessages.mapNotNull { msg ->
                    val payload = msg.sharedData[path] as? ByteArray
                    payload?.let {
                        val value: Value = json.decodeFromString(dataSharingMethod.serializer, it.decodeToString())
                        msg.senderId to value
                    }
                }.toMap()
            }
        }
    }
}

object FakeBroker {
    internal val subscribers = mutableMapOf<Uuid, FakeMailbox>()

    fun register(deviceId: Uuid, mailbox: FakeMailbox) {
        subscribers[deviceId] = mailbox
    }

    fun dispatch(sender: Uuid, outbound: OutboundEnvelope<Uuid>) {
        subscribers.forEach { (receiverId, mailbox) ->
            if (receiverId != sender) {
                val factory = object : SerializedMessageFactory<Uuid, Any?>(Json) {}
                val msg = outbound.prepareMessageFor(receiverId, factory)
                mailbox.deliverableReceived(msg)
            }
        }
    }

    fun clear() {
        subscribers.clear()
    }
}
