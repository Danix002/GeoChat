package it.unibo.collektive.model

import kotlin.uuid.Uuid

/**
 * TODO: doc
 */
data class Message(
    val text: String,
    val userName: String,
    val sender: Uuid,
    val receiver: Uuid,
    val time: String,
    val distance: Float,
    val id: Int = generateId(text, userName, sender, receiver, time, distance)
) {
    companion object {
        fun generateId(
            text: String,
            userName: String,
            sender: Uuid,
            receiver: Uuid,
            time: String,
            distance: Float
        ): Int {
            return "$text|$userName|$sender|$receiver|$time|$distance".hashCode()
        }
    }
}
