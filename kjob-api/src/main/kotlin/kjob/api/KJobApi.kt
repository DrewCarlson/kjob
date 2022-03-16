package kjob.api

import com.cronutils.model.CronType
import com.cronutils.model.definition.CronDefinitionBuilder
import com.cronutils.model.time.ExecutionTime
import com.cronutils.parser.CronParser
import kjob.core.BaseKJob
import kjob.core.Job
import kjob.core.KronJob
import kjob.core.extension.BaseExtension
import kjob.core.extension.ExtensionId
import kjob.core.extension.ExtensionModule
import kjob.core.job.JobExecutionType.BLOCKING
import kjob.core.job.JobExecutionType.NON_BLOCKING
import kjob.core.job.JobStatus
import kjob.core.job.ScheduledJob
import kjob.inmem.InMemKJob
import kjob.jdbi.JdbiKJob
import kjob.mongo.MongoKJob
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.json.*
import java.time.ZonedDateTime
import java.util.*

object KjobApiExtension : ExtensionId<KjobApiEx>

class KjobApiEx(
    @Suppress("unused")
    private val config: Configuration,
    private val kjobConfig: BaseKJob.Configuration,
    private val kjob: BaseKJob<BaseKJob.Configuration>
) : BaseExtension(KjobApiExtension) {
    class Configuration : BaseExtension.Configuration()

    private val jobStatuses by lazy { JobStatus.values().toSet() }
    internal val instanceId by lazy { kjob.id.toString() }
    private val cronParser by lazy {
        CronParser(CronDefinitionBuilder.instanceDefinitionFor(CronType.QUARTZ))
    }

    internal fun shareDatabase(other: KjobApiEx): Boolean {
        val mongo = (kjobConfig as? MongoKJob.Configuration)
        val otherMongo = (other.kjobConfig as? MongoKJob.Configuration)
        val jdbi = (kjobConfig as? JdbiKJob.Configuration)
        val otherJdbi = (other.kjobConfig as? JdbiKJob.Configuration)
        return if (mongo != null && otherMongo != null) {
            mongo.databaseName == otherMongo.databaseName &&
                    mongo.jobCollection == otherMongo.jobCollection &&
                    mongo.connectionString == otherMongo.connectionString &&
                    mongo.client == otherMongo.client
        } else if (jdbi != null && otherJdbi != null) {
            jdbi.handle == otherJdbi.handle &&
                    jdbi.jdbi == otherJdbi.jdbi &&
                    jdbi.connectionString == otherJdbi.connectionString &&
                    jdbi.jobTableName == otherJdbi.jobTableName
        } else false
    }

    internal fun jobTypes(): List<JsonObject> {
        val jobObjects = kjob.jobRegister()
            .run { jobs(BLOCKING) + jobs(NON_BLOCKING) }
            .map(kjob.jobRegister()::get)

        return jobObjects.map { runnableJob ->
            val propNames = (runnableJob.job as? Job)?.propNames
            buildJsonObject {
                put("name", runnableJob.job.name)
                put("maxRetries", runnableJob.maxRetries)
                put("executionType", runnableJob.executionType.name)
                (runnableJob.job as? KronJob)?.also { kron ->
                    putJsonObject("kron") {
                        val cron = cronParser.parse(kron.cronExpression)
                        val executionTime =  ExecutionTime.forCron(cron)
                        put("expression", kron.cronExpression)
                        putJsonObject("executionTime") {
                            val now = ZonedDateTime.now(kjob.clock)
                            val timeUntilNext = executionTime.timeToNextExecution(now)
                            val next = executionTime.nextExecution(now)
                            val previous = executionTime.lastExecution(now)
                            if (next.isPresent) {
                                put("next", next.get().toInstant().toString())
                            } else {
                                put("next", JsonNull)
                            }
                            if (previous.isPresent) {
                                put("previous", previous.get().toInstant().toString())
                            } else {
                                put("previous", JsonNull)
                            }
                            if (timeUntilNext.isPresent) {
                                put("millisUntilNext", timeUntilNext.get().toMillis())
                            } else {
                                put("millisUntilNext", JsonNull)
                            }
                        }
                    }
                } ?: put("kron", JsonNull)
                if (propNames == null) {
                    put("propNames", JsonArray(emptyList()))
                } else {
                    put("propNames", JsonArray(propNames.map(::JsonPrimitive)))
                }
            }
        }
    }

    internal suspend fun jobCounts(filterNames: Set<String>?): Map<JobStatus, Int> {
        val jobsResult = jobs(filterNames, null, null)
        val allJobStatuses = jobsResult.mapNotNull { it["status"]?.jsonPrimitive?.contentOrNull }
        return JobStatus.values().associateWith { status ->
            allJobStatuses.filter { it == status.name }.size
        }
    }

    internal suspend fun job(id: String): JsonObject? {
        return kjob.jobRepository.get(id)?.toJsonObject()
    }

    internal suspend fun jobs(
        names: Set<String>?,
        statuses: Set<JobStatus>?,
        limit: Int?,
    ): List<JsonObject> {
        return kjob.jobRepository
            .findNext(names ?: emptySet(), statuses ?: jobStatuses, limit ?: Int.MAX_VALUE)
            .map { job -> job.toJsonObject() }
            .toList()
    }

    internal fun instance(): JsonObject {
        return buildJsonObject {
            put("id", kjob.id.toString())
            putJsonObject("clock") {
                put("zone", kjob.clock.zone.id)
                put("millis", kjob.clock.millis())
            }
            putJsonObject("config") {
                put("isWorker", kjob.config.isWorker)
                put("maxRetries", kjob.config.maxRetries)
                put("cleanupSize", kjob.config.cleanupSize)
                put("blockingMaxJobs", kjob.config.blockingMaxJobs)
                put("nonBlockingMaxJobs", kjob.config.nonBlockingMaxJobs)
                put("defaultJobExecutor", kjob.config.defaultJobExecutor.name)
                put("cleanupPeriodInSeconds", kjob.config.cleanupPeriodInSeconds)
                put("jobExecutionPeriodInSeconds", kjob.config.jobExecutionPeriodInSeconds)
                put("keepAliveExecutionPeriodInSeconds", kjob.config.keepAliveExecutionPeriodInSeconds)

                (kjobConfig as? MongoKJob.Configuration)?.apply {
                    put("database", "mongodb")
                    put("databaseName", databaseName)
                    put("jobCollection", jobCollection)
                    put("lockCollection", lockCollection)
                    put("expireLockInMinutes", expireLockInMinutes)
                }
                (kjobConfig as? JdbiKJob.Configuration)?.apply {
                    put("database", "jdbi")
                    put("jobTableName", jobTableName)
                    put("lockTableName", lockTableName)
                    put("expireLockInMinutes", expireLockInMinutes)
                }
                (kjobConfig as? InMemKJob.Configuration)?.apply {
                    put("database", "memory")
                    put("expireLockInMinutes", expireLockInMinutes)
                }
            }
        }
    }

    private fun ScheduledJob.toJsonObject(): JsonObject {
        val propsMap = settings.properties.toJsonObject()
        return buildJsonObject {
            put("id", id)
            put("statusMessage", statusMessage)
            put("retries", retries)
            put("kjobId", kjobId?.toString())
            put("status", status.name)
            put("updatedAt", updatedAt.toString())
            put("createdAt", createdAt.toString())
            put("runAt", runAt?.toString())
            putJsonObject("settings") {
                put("id", settings.id)
                put("name", settings.name)
                put("props", JsonObject(propsMap))
            }
            putJsonObject("progress") {
                put("max", progress.max)
                put("step", progress.step)
                put("startedAt", progress.startedAt?.toString())
                put("completedAt", progress.completedAt?.toString())
            }
        }
    }

    private fun Map<String, Any>.toJsonObject(): JsonObject {
        return JsonObject(mapValues { (_, value) ->
            when (value) {
                is Number -> JsonPrimitive(value)
                is String -> JsonPrimitive(value)
                is Boolean -> JsonPrimitive(value)
                is List<*> -> {
                    if (value.isEmpty()) {
                        JsonArray(emptyList())
                    } else {
                        val first = value.first()
                        @Suppress("UNCHECKED_CAST")
                        JsonArray(
                            when (first) {
                                is Number -> (value as List<Number>).map(::JsonPrimitive)
                                is String -> (value as List<String>).map(::JsonPrimitive)
                                is Boolean -> (value as List<Boolean>).map(::JsonPrimitive)
                                else -> emptyList()
                            }
                        )
                    }
                }
                else -> JsonNull
            }
        })
    }
}

object KJobApiModule :
    ExtensionModule<KjobApiEx, KjobApiEx.Configuration, BaseKJob<BaseKJob.Configuration>, BaseKJob.Configuration> {
    override val id: ExtensionId<KjobApiEx> = KjobApiExtension
    override fun create(
        configure: KjobApiEx.Configuration.() -> Unit,
        kjobConfig: BaseKJob.Configuration
    ): (BaseKJob<BaseKJob.Configuration>) -> KjobApiEx {
        return { KjobApiEx(KjobApiEx.Configuration().apply(configure), kjobConfig, it) }
    }
}