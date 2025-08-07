package it.unibo.collektive.model

import it.unibo.collektive.viewmodels.utils.TimeProvider

class SystemTimeProvider : TimeProvider {
    override fun currentTimeMillis(): Long = System.currentTimeMillis()
}
