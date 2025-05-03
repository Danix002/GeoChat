package it.unibo.collektive.viewmodels

import android.location.Location
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.unibo.collektive.Collektive
import it.unibo.collektive.model.Message
import it.unibo.collektive.model.Params
import it.unibo.collektive.network.mqtt.MqttMailbox
import it.unibo.collektive.stdlib.spreading.gradientCast
import it.unibo.collektive.stdlib.util.Point3D
import it.unibo.collektive.stdlib.util.euclideanDistance3D
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import kotlin.Float.Companion.POSITIVE_INFINITY
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

class MessagesViewModel(private val dispatcher: CoroutineDispatcher = Dispatchers.IO) : ViewModel() {
    private val _dataFlow = MutableStateFlow<Pair<Uuid?, Triple<Float, String, String>?>>(null to null)
    private val _senders = MutableStateFlow<Map<Uuid, Triple<Float, String, String>>>(emptyMap())
    private val _devices = MutableStateFlow<Map<Uuid, Triple<Float, String, String>>>(emptyMap())
    private val _online = MutableStateFlow(false)
    private val _messaging = MutableStateFlow(false)
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    private val _received = MutableStateFlow<Map<Uuid, List<Params>>>(emptyMap())

    /**
     * Minimum propagation time required for the entire process of forwarding a message.
     */
    val MINIMUM_TIME_TO_SEND = 5.seconds

    /**
     * TODO: doc.
     */
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    /**
     * TODO: doc.
     */
    val messaging: StateFlow<Boolean> get() = _messaging

    /**
     * TODO: doc
     */
    private fun addNewMessageToList(received: Map<Uuid, List<Params>>) {
        val tmp = this._messages.value.toMutableList()
        tmp += received.values.flatten().map { newMessage ->
            var minute = newMessage.timestamp.minute.toString()
            if(newMessage.timestamp.minute < 10){
                minute = "0$minute"
            }
            Message(
                text = newMessage.message,
                userName = newMessage.to.second,
                sender = newMessage.to.first,
                receiver = newMessage.from.first,
                time = "${newMessage.timestamp.hour}:$minute",
                distance = newMessage.distance.toFloat()
            )
        }.filterNot { msg ->
            tmp.any { existing ->
                existing.sender == msg.sender && existing.receiver == msg.receiver && existing.text == msg.text
            }
        }
        this._messages.value = tmp
        Log.i("MessagesViewModel", "Message list: ${_messages.value}")
    }

    /**
     * TODO: doc
     */
    private fun addNewMessageToList(msg: Message) {
        val tmp = this._messages.value.toMutableList()
        if(!tmp.any { it.id == msg.id }){
            tmp += msg
        }
        this._messages.value = tmp
        Log.i("MessagesViewModel", "Message list: ${_messages.value}")
    }

    /**
     * Online devices in the chat page.
     */
    fun setOnlineStatus(flag: Boolean){
        this._online.value = flag

    }

    /**
     * TODO: doc
     */
    fun setMessagingFlag(flag: Boolean){
        this._messaging.value = flag
    }

    /**
     * TODO: doc
     */
    fun listenIntentions(
        distance: Float,
        nearbyDevicesViewModel: NearbyDevicesViewModel,
        position: Location?,
        userName: String,
        message: String,
        time: LocalDateTime
    ) {
        if(position != null) {
            viewModelScope.launch {
                val coordinates = Point3D(Triple(position.latitude, position.longitude, position.altitude))
                val program = spreadIntentionToSendMessage(
                    isSender = messaging.value,
                    deviceId = nearbyDevicesViewModel.deviceId,
                    userName = userName,
                    distance = distance,
                    position = coordinates,
                    message = message
                )
                while (_online.value) {
                    val newResult = program.cycle()
                    _dataFlow.value = newResult
                    if(newResult.second.first != POSITIVE_INFINITY) {
                        val tmp = _senders.value.toMutableMap()
                        tmp += newResult.first to (Triple(newResult.second.first, newResult.second.second, newResult.second.third))
                        _senders.value = tmp
                        if(newResult.first == nearbyDevicesViewModel.deviceId && _messaging.value){
                            var minute = time.minute.toString()
                            if(time.minute < 10){
                                minute = "0$minute"
                            }
                            addNewMessageToList(
                                Message(
                                    text = message,
                                    userName = userName,
                                    sender = nearbyDevicesViewModel.deviceId,
                                    receiver = nearbyDevicesViewModel.deviceId,
                                    time = "${time.hour}:$minute",
                                    distance = 0f
                                )
                            )
                        }
                    }
                    _devices.value = nearbyDevicesViewModel.getListOfDevices(_senders.value).cycle()
                    _received.value = nearbyDevicesViewModel.transformDistances(
                        senders = _senders.value,
                        devicesValues = _devices.value,
                        position = coordinates,
                        time = time
                    ).cycle()
                    Log.i("MessagesViewModel", "Received message: ${_received.value}")
                    if(_received.value.isNotEmpty()) {
                        addNewMessageToList(_received.value.toMap())
                    }
                    delay(1.seconds)
                }
            }
        }else{
            throw IllegalStateException("Position could not be retrieved")
        }
    }

    /**
     * TODO: doc
     */
    private suspend fun spreadIntentionToSendMessage(
        isSender: Boolean,
        deviceId: Uuid,
        userName: String,
        distance: Float,
        position: Point3D,
        message: String
    ): Collektive<Uuid, Pair<Uuid, Triple<Float, String, String>>> =
        Collektive(deviceId, MqttMailbox(deviceId, "broker.hivemq.com", dispatcher = dispatcher)) {
            gradientCast(
                source = isSender,
                local = deviceId to Triple(distance, userName, message),
                metric = euclideanDistance3D(position),
                accumulateData = { fromSource, toNeighbor, dist ->
                    if (fromSource + toNeighbor <= distance.toDouble()) {
                        dist
                    } else {
                        deviceId to Triple(POSITIVE_INFINITY, userName, "")
                    }
                }
            )
        }.also {
            delay(1.seconds)
        }
}
