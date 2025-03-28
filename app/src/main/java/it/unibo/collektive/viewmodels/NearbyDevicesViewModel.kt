package it.unibo.collektive.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.unibo.collektive.Collektive
import it.unibo.collektive.aggregate.api.Aggregate.Companion.neighboring
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

    private suspend fun collektiveProgram(): Collektive<Uuid, Set<Uuid>> =
        Collektive(deviceId, MqttMailbox(deviceId, host = "broker.hivemq.com", dispatcher = dispatcher)) {
            neighboring(localId).neighbors.toSet()
        }

    /**
     * Start the Collektive program.
     */
    fun startCollektiveProgram() {
        viewModelScope.launch {
            Log.i("NearbyDevicesViewModel", "Starting Collektive program...")
            val program = collektiveProgram()
            _connectionFlow.value = ConnectionState.CONNECTED
            Log.i("NearbyDevicesViewModel", "Collektive program started")
            while (true) {
                val newResult = program.cycle()
                _dataFlow.value = newResult
                delay(1.seconds)
                Log.i("NearbyDevicesViewModel", "New nearby devices: $newResult")
            }
        }
    }

    /**
     * Change user name of local device.
     */
    fun setUserName(value: String){
        this._userName.value = value
    }
}
