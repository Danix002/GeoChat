package it.unibo.collektive.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.unibo.collektive.Collektive
import it.unibo.collektive.aggregate.api.Aggregate.Companion.neighboring
import it.unibo.collektive.network.mqtt.MqttMailbox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class NearbyDevicesViewModel : ViewModel() {
    private val _dataFlow = MutableStateFlow<Set<Uuid>>(emptySet())

    /**
     * The set of nearby devices.
     */
    val dataFlow: StateFlow<Set<Uuid>> = _dataFlow.asStateFlow()

    private suspend fun collektiveProgram(): Collektive<Uuid, Set<Uuid>> {
        val deviceId = Uuid.random()
        return Collektive(deviceId, MqttMailbox(deviceId, host = "broker.hivemq.com", dispatcher = Dispatchers.IO)) {
            neighboring(localId).neighbors.toSet()
        }
    }

    fun startCollektiveProgram() {
        viewModelScope.launch {
            Log.i("NearbyDevicesViewModel", "Starting Collektive program...")
            val program = collektiveProgram()
            Log.i("NearbyDevicesViewModel", "Collektive program started")
            while (true) {
                val newResult = program.cycle()
                _dataFlow.value = newResult
                delay(1.seconds)
                Log.i("NearbyDevicesViewModel", "New nearby devices: $newResult")
            }
        }
    }
//
//    companion object {
//        val Factory: ViewModelProvider.Factory = viewModelFactory {
//            initializer {
//                val app = checkNotNull(this[APPLICATION_KEY])
//                NearbyDevicesViewModel(app)
//            }
//        }
//    }
}
