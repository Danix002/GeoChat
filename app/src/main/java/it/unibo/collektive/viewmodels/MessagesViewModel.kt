package it.unibo.collektive.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.unibo.collektive.model.Message
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.uuid.Uuid

class MessagesViewModel : ViewModel() {
    /**private val _messages = MutableStateFlow<List<Message>>(emptyList())*/
    private val _messages = MutableStateFlow(
        listOf(
            Message("nil", "User", Uuid.random(), Uuid.random(), 0.0f, 0.0f),
            Message("nil", "User", Uuid.random(), Uuid.random(), 0.0f, 0.0f),
            Message("nil", "User", Uuid.random(), Uuid.random(), 0.0f, 0.0f),
            Message("nil", "User", Uuid.random(), Uuid.random(), 0.0f, 0.0f),
            Message("nil", "User", Uuid.random(), Uuid.random(), 0.0f, 0.0f),
            Message("nil", "User", Uuid.random(), Uuid.random(), 0.0f, 0.0f),
            Message("nil", "User", Uuid.random(), Uuid.random(), 0.0f, 0.0f),
            Message("nil", "User", Uuid.random(), Uuid.random(), 0.0f, 0.0f),
            Message("nil", "User", Uuid.random(), Uuid.random(), 0.0f, 0.0f)
            )
    )
    val messages: StateFlow<List<Message>> get() = _messages

    // TODO
    /*fun addNewMessageToList() {

    }*/

    /*fun sendMessage() {

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
}
