package it.unibo.collektive.viewmodels

import android.location.Location
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.unibo.collektive.Collektive
import it.unibo.collektive.aggregate.Field
import it.unibo.collektive.model.Message
import it.unibo.collektive.model.Params
import it.unibo.collektive.network.mqtt.MqttMailbox
import it.unibo.collektive.stdlib.spreading.gradientCast
import it.unibo.collektive.stdlib.util.Point3D
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
    private val _senders = MutableStateFlow<Map<Uuid, Pair<Float, String>>>(emptyMap())
    private val _devices = MutableStateFlow<List<Triple<Uuid, Float, String>>>(emptyList())
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
            val coordinates = Point3D(Triple(position.latitude, position.longitude, position.altitude))
            viewModelScope.launch {
                val distances = nearbyDevicesViewModel.getDistanceToDevices(coordinates)
                Log.i("MessagesViewModel", "Distances: ${distances.cycle()}")
                val program = spreadIntentionToSendMessage(
                    isSender = getMessagingFlag(),
                    deviceId = nearbyDevicesViewModel.deviceId,
                    distance = distance,
                    metric = distances.cycle(),
                    userName = userName
                )
                while (_online.value) {
                    val newResult = program.cycle()
                    _dataFlow.value = newResult
                    /**
                     * TODO: saluzione provvisoria prima di mettere il timer
                     */
                    if(newResult.second != POSITIVE_INFINITY) {
                        _senders.value += newResult.first to (newResult.second to newResult.third)
                        Log.i("MessagesViewModel", "Senders: ${_senders.value}")
                    }
                    /**_devices.value = nearbyDevicesViewModel.getListOfDevices(_senders.value).cycle()*/
                    delay(5.seconds)
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
        distance: Float,
        metric: Field<Uuid, Double>,
        userName: String,
    ): Collektive<Uuid, Triple<Uuid, Float, String>> =
        Collektive(deviceId, MqttMailbox(deviceId, "broker.hivemq.com", dispatcher = dispatcher)) {
            gradientCast(
                source = isSender,
                local = Triple(deviceId, distance, userName),
                metric = metric,
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
