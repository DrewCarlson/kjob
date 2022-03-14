package kjob.core.internal.scheduler

import io.kotest.core.TestConfiguration
import kjob.core.job.JobProgress
import kjob.core.job.JobSettings
import kjob.core.job.JobStatus
import kjob.core.job.JobStatus.*
import kjob.core.job.ScheduledJob
import kjob.core.repository.now
import kjob.core.utils.nextAlphanumericString
import java.time.Instant
import java.util.*
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledThreadPoolExecutor
import kotlin.random.Random

fun js(
        id: String = Random.nextAlphanumericString(5),
        name: String = "test-job",
        props: Map<String, Any> = emptyMap()
) = JobSettings(id, name, props)

fun jp(
        step: Long = 0,
        max: Long? = null,
        startedAt: Instant? = null,
        completedAt: Instant? = null
) = JobProgress(step, max, startedAt, completedAt)

fun sj(
        id: String = UUID.randomUUID().toString(),
        status: JobStatus = CREATED,
        runAt: Instant? = null,
        message: String? = null,
        retries: Int = 0,
        kjobId: UUID? = null,
        createdAt: Instant = now(),
        updatedAt: Instant = now(),
        settings: JobSettings = js(),
        progress: JobProgress = jp()
) = ScheduledJob(
        id,
        status,
        runAt,
        message,
        retries,
        kjobId,
        createdAt,
        updatedAt,
        settings,
        progress
)

fun TestConfiguration.newScheduler(): ScheduledExecutorService = autoClose(object : ScheduledThreadPoolExecutor(1), AutoCloseable {
    override fun close() {
        shutdown()
    }
})