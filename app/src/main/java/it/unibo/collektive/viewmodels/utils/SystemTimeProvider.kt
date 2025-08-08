package it.unibo.collektive.viewmodels.utils

import java.time.LocalDateTime

class SystemTimeProvider : TimeProvider {
    override fun currentTimeMillis(): Long = System.currentTimeMillis()

    override fun now(): LocalDateTime = LocalDateTime.now()
}
