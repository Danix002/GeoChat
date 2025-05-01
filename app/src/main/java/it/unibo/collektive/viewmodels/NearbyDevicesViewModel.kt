package it.unibo.collektive.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.unibo.collektive.Collektive
import it.unibo.collektive.aggregate.Field
import it.unibo.collektive.aggregate.api.mapNeighborhood
import it.unibo.collektive.aggregate.api.neighboring
import it.unibo.collektive.model.Params
import it.unibo.collektive.network.mqtt.MqttMailbox
import it.unibo.collektive.stdlib.spreading.distanceTo
import it.unibo.collektive.stdlib.util.Point3D
import it.unibo.collektive.stdlib.util.euclideanDistance3D
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.Float.Companion.POSITIVE_INFINITY
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

/**
 * A ViewModel that manages the list of nearby devices.
 */
class NearbyDevicesViewModel(private val dispatcher: CoroutineDispatcher = Dispatchers.IO) : ViewModel() {
    private val _dataFlow = MutableStateFlow<Set<Uuid>>(emptySet())
    private val _connectionFlow = MutableStateFlow(ConnectionState.DISCONNECTED)
    private val _userName = MutableStateFlow("User")

    /**
     * The connection state.
     */
    enum class ConnectionState {
        /**
         * Connected to the broker.
         */
        CONNECTED,

        /**
         * Disconnected from the broker.
         */
        DISCONNECTED,
    }

    /**
     * The set of nearby devices.
     */
    val dataFlow: StateFlow<Set<Uuid>> = _dataFlow.asStateFlow()

    /**
     * The connection state.
     */
    val connectionFlow: StateFlow<ConnectionState> = _connectionFlow.asStateFlow()

    /**
     * The user name of local device.
     */
    val userName: StateFlow<String> get() = _userName

    /**
     * The local device ID.
     */
    val deviceId = Uuid.random()

    /**
     * Change user name of local device.
     */
    fun setUserName(value: String){
        this._userName.value = value
    }

    /**
     * TODO: doc
     */
     suspend fun collektiveProgram(): Collektive<Uuid, Set<Uuid>> =
        Collektive(deviceId, MqttMailbox(deviceId, host = "broker.hivemq.com", dispatcher = dispatcher)) {
            neighboring(localId).neighbors.toSet()
        }

    /**
     * Start the Collektive program.
     */
    fun startCollektiveProgram() {
        viewModelScope.launch {
            val program = collektiveProgram()
            _connectionFlow.value = ConnectionState.CONNECTED
            while (true) {
                val newResult = program.cycle()
                _dataFlow.value = newResult
                delay(1.seconds)
                Log.i("NearbyDevicesViewModel", "New nearby devices: $newResult")
            }
        }
    }

    /**
     * TODO: doc
     */
    suspend fun getListOfDevices(sender: Map<Uuid, Pair<Float, String>>): Collektive<Uuid, List<Triple<Uuid, Float, String>>> =
        Collektive(deviceId, MqttMailbox(deviceId, "broker.hivemq.com", dispatcher = dispatcher)) {
            mapNeighborhood { id ->
                if (sender.containsKey(id)) {
                    Triple(id, sender[id]!!.first, sender[id]!!.second)
                } else {
                    Triple(id, POSITIVE_INFINITY, "")
                }
            }.neighborsValues
        }

    /**
     * TODO: doc
     */
    suspend fun getDistanceToDevices(position: Point3D): Collektive<Uuid, Field<Uuid, Double>> =
        Collektive(deviceId, MqttMailbox(deviceId, "broker.hivemq.com", dispatcher = dispatcher)) {
            euclideanDistance3D(position)
        }

    suspend fun computeDistances(
        senders: Map<Uuid, Pair<Float, String>>,
        devicesValues: List<Triple<Uuid, Float, String>>,
        position: Point3D,
        isSender: Boolean,
        userName: String = this._userName.value
    ) : Collektive<Uuid, Map<Uuid, List<Params>>> = TODO()
}
