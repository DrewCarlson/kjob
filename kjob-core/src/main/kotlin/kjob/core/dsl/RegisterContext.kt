package kjob.core.dsl

import kjob.core.BaseJob
import kjob.core.KJob
import kjob.core.job.JobExecutionType

@JobDslMarker
class JobRegisterContext<J : BaseJob, JC : JobContext<J>> internal constructor(configuration: KJob.Configuration) {
    /**
     * Override the default execution type defined in the configuration
     */
    var executionType: JobExecutionType = configuration.defaultJobExecutor

    /**
     * Override the default maxRetries defined in the configuration
     */
    var maxRetries: Int = configuration.maxRetries

    /**
     * Defines the code that should be executed when the job is scheduled
     */
    fun execute(block: suspend JC.() -> Unit): KJobFunctions<J, JC> {
        return KJobFunctions(block)
    }
}