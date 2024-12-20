package kjob.inmem

import kjob.core.KJob
import kjob.core.KJobFactory

object InMem : KJobFactory<InMemKJob, InMemKJob.Configuration> {
    override fun create(configure: InMemKJob.Configuration.() -> Unit): KJob {
        return InMemKJob(InMemKJob.Configuration().apply(configure))
    }
}
