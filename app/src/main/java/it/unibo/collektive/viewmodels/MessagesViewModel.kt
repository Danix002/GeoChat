package it.unibo.collektive.viewmodels

import android.location.Location
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.unibo.collektive.Collektive
import it.unibo.collektive.model.Message
import it.unibo.collektive.model.Params
import it.unibo.collektive.network.mqtt.MqttMailbox
import it.unibo.collektive.stdlib.lists.FieldedCollectionsExtensions.last
import it.unibo.collektive.stdlib.spreading.gradientCast
import it.unibo.collektive.stdlib.util.Point3D
import it.unibo.collektive.stdlib.util.euclideanDistance3D
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import kotlin.Float.Companion.POSITIVE_INFINITY
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

class MessagesViewModel(private val dispatcher: CoroutineDispatcher = Dispatchers.IO) : ViewModel() {
    private val _dataFlow = MutableStateFlow<Triple<Uuid?, Float?, String?>>(Triple(null, null, null))
    private var _senders: Map<Uuid, Pair<Float, String>> = emptyMap()
    private val _online = MutableStateFlow(false)
    private val _messagging = MutableStateFlow(false)
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> get() = _messages

    // TODO
    /*fun addNewMessageToList() {

    }*/

    /*fun formatDistanceInKm(): Int {

    }*/

    /*fun formatDistanceInMt(): Int {

    }*/

    /*fun formatTimeInSc(): Int {

    }*/

    /*fun formatTimeInMin(): Int {

    }*/

    /*fun updateUserNameToList(){

    }*/

    fun setOnlineStatus(flag: Boolean){
        this._online.value = flag

    }

    fun setMessagingFlag(flag: Boolean){
        this._messagging.value = flag
    }

    fun getMessagingFlag(): Boolean{
        return this._messagging.value
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
                    isSender = getMessagingFlag(),
                    deviceId = nearbyDevicesViewModel.deviceId,
                    userName = userName,
                    distance = distance,
                    position = coordinates
                )
                while (_online.value) {
                    /**
                     * Il messaggio deve essere inviato e refreshato ad ogni iterazione fino a che
                     * message flag è uguale a true, verrà impostato a false quando il timer è scaduto
                     */
                    Log.i(
                        "MessagesViewModel",
                        "Distances: ${nearbyDevicesViewModel.getDistanceToDevices(coordinates).cycle()}"
                    )
                    val newResult = program.cycle()
                    _dataFlow.value = newResult
                    if(
                        (newResult.second != POSITIVE_INFINITY && newResult.first != nearbyDevicesViewModel.deviceId) ||
                        (newResult.second != POSITIVE_INFINITY && newResult.first == nearbyDevicesViewModel.deviceId && getMessagingFlag())
                    ) {
                        _senders += newResult.first to (newResult.second to newResult.third)
                    }
                    Log.i("MessagesViewModel", "Senders: $_senders")
                    delay(1.seconds)
                    /**
                     * TODO: pulizia di tutte le strutture dati utilizzate per inoltrare il messaggio
                     */
                    Log.i("MessagesViewModel", "Flag for messaging: ${getMessagingFlag()}")
                    _senders = emptyMap()
                    Log.i("MessagesViewModel", "Senders: $_senders")
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
        position: Point3D
    ): Collektive<Uuid, Triple<Uuid, Float, String>> =
        Collektive(deviceId, MqttMailbox(deviceId, "broker.hivemq.com", dispatcher = dispatcher)) {
            gradientCast(
                source = isSender,
                local = Triple(deviceId, distance, userName),
                metric = euclideanDistance3D(position),
                accumulateData = { fromSource, toNeighbor, dist ->
                    if (fromSource + toNeighbor <= distance.toDouble()) {
                        dist
                    } else {
                        Triple(deviceId, POSITIVE_INFINITY, userName)
                    }
                }
            )
        }
}
