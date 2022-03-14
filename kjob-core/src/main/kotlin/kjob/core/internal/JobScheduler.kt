package kjob.core.internal

import kjob.core.job.JobSettings
import kjob.core.job.ScheduledJob
import kjob.core.repository.JobRepository
import org.slf4j.LoggerFactory
import java.time.Instant

interface JobScheduler {
    suspend fun schedule(settings: JobSettings, runAt: Instant? = null): ScheduledJob
}

internal class DefaultJobScheduler(private val jobRepository: JobRepository) : JobScheduler {
    private val logger = LoggerFactory.getLogger(javaClass)

    override suspend fun schedule(settings: JobSettings, runAt: Instant?): ScheduledJob = settings.run {
        return if (jobRepository.exist(id)) {
            error("Job '$name' with id '$id' has already been scheduled.")
        } else {
            jobRepository.save(this, runAt).also { logger.debug("Scheduled new job '${it.settings.name} @ '$runAt'") }
        }
    }
}