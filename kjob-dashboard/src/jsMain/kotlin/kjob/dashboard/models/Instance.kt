package kjob.dashboard.models

import kotlinx.serialization.Serializable

@Serializable
data class Instance(
    val id: String,
    val clock: InstanceClock,
    val config: InstanceConfig,
)

@Serializable
data class InstanceClock(
    val zone: String,
    val millis: Long,
)

@Serializable
data class InstanceConfig(
    val isWorker: Boolean,
    val maxRetries: Int,
    val cleanupSize: Int,
    val blockingMaxJobs: Int,
    val nonBlockingMaxJobs: Int,
    val defaultJobExecutor: String,
    val cleanupPeriodInSeconds: Int,
    val jobExecutionPeriodInSeconds: Int,
    val database: String,
    val jobTableName: String? = null,
    val lockTableName: String? = null,
    val expireLockInMinutes: Int,
)