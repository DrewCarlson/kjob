package kjob.inmem

import kjob.core.BaseKJob
import kjob.core.repository.JobRepository
import kjob.core.repository.LockRepository
import kjob.inmem.repository.InMemJobRepository
import kjob.inmem.repository.InMemLockRepository
import java.time.Clock

class InMemKJob(config: Configuration) : BaseKJob<InMemKJob.Configuration>(config) {

    class Configuration : BaseKJob.Configuration() {
        /**
         * The timeout until a kjob instance is considered dead if no 'I am alive' notification occurred
         */
        var expireLockInMinutes = 5L
    }

    override val jobRepository: JobRepository = InMemJobRepository(Clock.systemUTC())

    override val lockRepository: LockRepository = InMemLockRepository(config, Clock.systemUTC())
}