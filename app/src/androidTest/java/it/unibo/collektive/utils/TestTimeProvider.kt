package it.unibo.collektive.utils

import it.unibo.collektive.viewmodels.utils.TimeProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler
import java.time.LocalDateTime

class TestTimeProvider(private val scheduler: TestCoroutineScheduler) : TimeProvider {
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun currentTimeMillis(): Long = scheduler.currentTime

    fun now(): LocalDateTime =
        java.time.Instant.ofEpochMilli(currentTimeMillis())
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDateTime()
}
