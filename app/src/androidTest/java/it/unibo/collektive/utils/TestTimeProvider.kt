package it.unibo.collektive.utils

import it.unibo.collektive.viewmodels.utils.TimeProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler
import java.time.LocalDateTime

class TestTimeProvider(private val scheduler: TestCoroutineScheduler) : TimeProvider {
    private val anchorMillis = System.currentTimeMillis()

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun currentTimeMillis(): Long = anchorMillis + scheduler.currentTime

    override fun now(): LocalDateTime = LocalDateTime.now()
}
