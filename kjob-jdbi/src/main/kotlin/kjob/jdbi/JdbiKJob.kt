package kjob.jdbi

import kjob.core.BaseKJob
import kjob.core.KJob
import kjob.core.KJobFactory
import kjob.core.repository.JobRepository
import kjob.core.repository.LockRepository
import kjob.jdbi.repository.JdbiJobRepository
import kjob.jdbi.repository.JdbiLockRepository
import org.jdbi.v3.core.Handle
import org.jdbi.v3.core.Jdbi
import java.time.Clock


class JdbiKJob(config: Configuration) : BaseKJob<JdbiKJob.Configuration>(config) {

    init {
        if (config.expireLockInMinutes * 60 <= config.keepAliveExecutionPeriodInSeconds)
            error("The lock expires before a new 'keep alive' has been scheduled. That will not work.")
    }

    companion object : KJobFactory<JdbiKJob, Configuration> {
        override fun create(configure: Configuration.() -> Unit): KJob {
            return JdbiKJob(Configuration().apply(configure))
        }
    }

    class Configuration : BaseKJob.Configuration() {
        /**
         * The jdbc specific connection string, no default is provided.
         * [connectionString], [handle], or [jdbi] must be specified.
         */
        var connectionString: String? = null

        /**
         * If [jdbi] is specified the [connectionString] will be ignored.
         * Useful if you already have a configured JDBI client without a [Handle].
         * [connectionString], [handle], or [jdbi] must be specified.
         */
        var jdbi: Jdbi? = null

        /**
         * If [handle] is specified [jdbi] and [connectionString] will be ignored.
         * Useful if you already have a shared [Handle] to use.
         * NOTE: If configured, this [Handle] will not automatically close when
         * the [JdbiKJob] instance shuts down.
         */
        var handle: Handle? = null

        /**
         * The table name for all jobs
         */
        var jobTableName = "kjobJobs"

        /**
         * The table name for kjob locks
         */
        var lockTableName = "kjobLocks"

        /**
         * Using the TTL feature of mongoDB to expire a lock (which means that after
         * this time a kjob instance is considered dead if no 'I am alive' notification occurred)
         */
        var expireLockInMinutes = 5L
    }

    private val jdbi = if (config.handle == null) {
        config.jdbi ?: buildJdbi()
    } else {
        null
    }

    private var handle: Handle? = jdbi?.open()

    private fun buildJdbi(): Jdbi {
        val connectionString = checkNotNull(config.connectionString) {
            "`JdbiKJob` configuration must provide `connectionString` or `jdbi`."
        }
        return Jdbi.create(connectionString)
    }

    override val jobRepository: JobRepository = JdbiJobRepository(
        { checkNotNull(config.handle ?: handle) },
        config,
        Clock.systemUTC()
    )

    override val lockRepository: LockRepository = JdbiLockRepository(
        { checkNotNull(config.handle ?: handle) },
        config,
        Clock.systemUTC()
    )

    override fun start(): KJob {
        handle = config.handle ?: jdbi?.open()
        (jobRepository as JdbiJobRepository).createTable()
        (lockRepository as JdbiLockRepository).createTable()
        return super.start()
    }

    override fun shutdown() {
        super.shutdown()
        (lockRepository as JdbiLockRepository).clearExpired()
        if (config.handle == null) {
            handle?.close()
            handle = null
        }
    }
}