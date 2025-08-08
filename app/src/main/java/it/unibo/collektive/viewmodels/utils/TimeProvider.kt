package it.unibo.collektive.viewmodels.utils

import java.time.LocalDateTime

interface TimeProvider {
    fun currentTimeMillis(): Long
    fun now(): LocalDateTime
}
