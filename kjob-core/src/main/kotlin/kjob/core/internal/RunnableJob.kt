package kjob.core.internal

import kjob.core.BaseJob
import kjob.core.dsl.JobContext
import kjob.core.job.JobExecutionType

interface RunnableJob {

    val job: BaseJob

    val executionType: JobExecutionType

    val maxRetries: Int

    suspend fun execute(context: JobContext<*>): JobResult
}