package it.unibo.collektive.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.unibo.collektive.Collektive
import it.unibo.collektive.aggregate.api.neighboring
import it.unibo.collektive.network.mqtt.MqttMailbox
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

/**
 * A ViewModel that manages the list of nearby devices.
 */
class NearbyDevicesViewModel(private val dispatcher: CoroutineDispatcher = Dispatchers.IO) : ViewModel() {
    private val _dataFlow = MutableStateFlow<Set<Uuid>>(emptySet())
    private val _connectionFlow = MutableStateFlow(ConnectionState.DISCONNECTED)
    private val _userName = MutableStateFlow("User")
    private val _online = MutableStateFlow(true)

    private val IP_HOST = "192.168.1.6"

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
     * Online devices in the home page.
     */
    fun setOnlineStatus(flag: Boolean){
        this._online.value = flag

    }

    /**
     * Creates a Collektive program that identifies the neighboring devices of the local node.
     *
     * This suspending function builds a Collektive computation that leverages MQTT-based
     * communication to retrieve the set of UUIDs corresponding to all neighboring devices
     * currently visible to the local device.
     *
     * The function uses the `neighboring` construct to inspect the local communication
     * neighborhood, extracting and returning the UUIDs of the devices with which the local
     * node can interact.
     *
     * @return A Collektive program that computes a set of UUIDs representing
     *         the neighboring devices of the current local node.
     */
     private suspend fun collektiveProgram(): Collektive<Uuid, Set<Uuid>> =
        Collektive(deviceId, MqttMailbox(deviceId, host = IP_HOST, dispatcher = dispatcher)) {
            neighboring(localId).neighbors.toSet()
        }

    /**
     * Start the Collektive program.
     */
    fun startCollektiveProgram() {
        viewModelScope.launch {
            val program = collektiveProgram()
            _connectionFlow.value = ConnectionState.CONNECTED
            while (_online.value) {
                val newResult = program.cycle()
                _dataFlow.value = newResult
                delay(1.seconds)
            }
        }
    }
}
