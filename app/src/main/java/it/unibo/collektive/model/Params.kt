package it.unibo.collektive.model

import kotlin.uuid.Uuid

/**
 * TODO: doc
 */
data class Params(
    val to: Pair<Uuid, String>,
    val from: Pair<Uuid, String>,
    val distanceForMessaging: Float,
    val distance: Float,
    val isSenderValues: Boolean
)
