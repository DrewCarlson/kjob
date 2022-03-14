package kjob.core.internal

import kjob.core.BaseJob
import kjob.core.KJob
import kjob.core.dsl.*
import kjob.core.job.JobExecutionType

class DefaultRunnableJob<J : BaseJob>(
        override val job: J,
        configuration: KJob.Configuration,
        block: JobRegisterContext<J, JobContext<J>>.(J) -> KJobFunctions<J, JobContext<J>>
) : RunnableJob {
    private val rjc = JobRegisterContext<J, JobContext<J>>(configuration)
    private val sjc = block(rjc, job)

    override val executionType: JobExecutionType = rjc.executionType
    override val maxRetries: Int = rjc.maxRetries

    @Suppress("UNCHECKED_CAST")
    override suspend fun execute(context: JobContext<*>): JobResult {
        return try {
            if (!context.start()) error("Failed to start execution.")
            sjc.executeFn(context as JobContext<J>)
            if (!context.complete()) error("Failed to complete execution.")
            sjc.completeFn(CompletionJobContext(context.scheduledJob(), context.logger))
            JobSuccessful
        } catch (t: Throwable) {
            context.logger.error("Execution failed", t)
            try {
                sjc.errorFn(ErrorJobContext(context.scheduledJob(), t, context.logger))
            } catch (t: Throwable) {
                context.logger.error("Error handler also failed", t)
            }
            JobError(t)
        }

    }
}