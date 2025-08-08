package it.unibo.collektive.utils

import it.unibo.collektive.viewmodels.utils.TimeProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class TestTimeProvider(
    private val scheduler: TestCoroutineScheduler
) : TimeProvider {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun currentTimeMillis(): Long = scheduler.currentTime

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun now(): LocalDateTime =
        LocalDateTime.ofInstant(
            Instant.ofEpochMilli(scheduler.currentTime),
            ZoneId.systemDefault()
        )
}

