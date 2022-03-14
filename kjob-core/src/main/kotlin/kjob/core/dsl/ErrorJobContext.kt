package kjob.core.dsl

import kjob.core.job.ScheduledJob
import org.slf4j.Logger

@JobDslMarker
class ErrorJobContext internal constructor(scheduledJob: ScheduledJob, val error: Throwable, val logger: Logger) {
    val jobName = scheduledJob.settings.name
    val jobId = scheduledJob.settings.id
}