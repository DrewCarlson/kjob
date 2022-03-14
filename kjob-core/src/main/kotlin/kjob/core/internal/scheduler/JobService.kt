package kjob.core.internal.scheduler

import kjob.core.internal.JobExecutor
import kjob.core.internal.JobRegister
import kjob.core.job.JobExecutionType
import kjob.core.job.JobStatus.CREATED
import kjob.core.job.JobStatus.SCHEDULED
import kjob.core.job.ScheduledJob
import kjob.core.repository.JobRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.ScheduledExecutorService
import kotlin.coroutines.CoroutineContext

internal class JobService(
    executorService: ScheduledExecutorService,
    period: Long,
    private val id: UUID,
    override val coroutineContext: CoroutineContext,
    private val jobRegister: JobRegister,
    private val jobExecutor: JobExecutor,
    private val jobRepository: JobRepository
) : SimplePeriodScheduler(executorService, period), CoroutineScope {
    private val logger = LoggerFactory.getLogger(javaClass)

    private suspend fun executeJob(scheduledJob: ScheduledJob) {
        val runnableJob = jobRegister.get(scheduledJob.settings.name)
        val isMyJob = jobRepository.update(scheduledJob.id, null, id, SCHEDULED, null, scheduledJob.retries)
        if (isMyJob) {
            jobExecutor.execute(runnableJob, scheduledJob, jobRepository)
        }
    }

    private suspend fun findAndExecuteJob(names: Set<String>): Boolean {
        if (names.isNotEmpty()) {
            return jobRepository.findNextOne(names, setOf(CREATED))?.let { executeJob(it) } != null
        }
        return false
    }

    private fun tryExecuteJob() {
        launch {
            var hasExecutedAJob = false
            do {
                if (jobExecutor.canExecute(JobExecutionType.BLOCKING))
                    hasExecutedAJob = findAndExecuteJob(jobRegister.jobs(JobExecutionType.BLOCKING))

                if (jobExecutor.canExecute(JobExecutionType.NON_BLOCKING))
                    hasExecutedAJob = hasExecutedAJob || findAndExecuteJob(jobRegister.jobs(JobExecutionType.NON_BLOCKING))
            } while (hasExecutedAJob)
        }
    }

    fun start(): Unit = run {
        logger.debug("Job service scheduled.")
        tryExecuteJob()
    }
}