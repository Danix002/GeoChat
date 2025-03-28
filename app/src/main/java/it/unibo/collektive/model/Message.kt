package it.unibo.collektive.model

import kotlin.uuid.Uuid

data class Message(
    val text: String,
    val userName: String,
    val sender: Uuid,
    val receiver: Uuid,
    val time: Float,
    val distance: Float
)
