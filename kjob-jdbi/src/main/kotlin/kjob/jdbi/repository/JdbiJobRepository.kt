package kjob.jdbi.repository

import kjob.core.job.JobProgress
import kjob.core.job.JobSettings
import kjob.core.job.JobStatus
import kjob.core.job.ScheduledJob
import kjob.core.repository.JobRepository
import kjob.jdbi.JdbiKJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.jdbi.v3.core.Handle
import org.jdbi.v3.core.JdbiException
import org.jdbi.v3.core.result.RowView
import org.jdbi.v3.core.statement.Update
import java.sql.Types
import java.time.Clock
import java.time.Instant
import java.util.*

internal class JdbiJobRepository(
    private val handleProvider: () -> Handle,
    config: JdbiKJob.Configuration,
    private val clock: Clock
) : JobRepository {

    constructor(handle: Handle, clock: Clock, conf: JdbiKJob.Configuration.() -> Unit) :
            this({ handle }, JdbiKJob.Configuration().apply(conf), clock)

    private val jobTable = config.jobTableName
    private var supportsSerialId = false

    fun createTable() {
        val handle = handleProvider()
        supportsSerialId = try {
            handle.execute("SELECT version();")
            true
        } catch (e: JdbiException) {
            false
        }
        val idType = if (supportsSerialId) "SERIAL" else "INTEGER PRIMARY KEY"
        val result = handle.execute(
            """
            CREATE TABLE IF NOT EXISTS $jobTable (
                id            $idType,
                status        TEXT NOT NULL,
                runAt         BIGINT,
                statusMessage TEXT,
                retries       INTEGER NOT NULL,
                kjobId        CHAR(36),
                createdAt     BIGINT NOT NULL,
                updatedAt     BIGINT NOT NULL,
                jobId         TEXT NOT NULL,
                name          TEXT,
                properties    TEXT,
                step          INTEGER NOT NULL,
                max           INTEGER,
                startedAt     BIGINT,
                completedAt   BIGINT
            );
            """.trimIndent()
        )
        check(result == 0 || result == 1) {
            "Failed to create job table with name '$jobTable': result == $result"
        }
    }

    override suspend fun exist(jobId: String): Boolean {
        val handle = handleProvider()
        return handle.createQuery("SELECT COUNT(id) FROM $jobTable WHERE jobId = :jobId")
            .bind("jobId", jobId)
            .mapTo(Int::class.java)
            .one() == 1
    }

    override suspend fun save(jobSettings: JobSettings, runAt: Instant?): ScheduledJob {
        val handle = handleProvider()
        val now = Instant.now(clock)
        val sj = ScheduledJob("", JobStatus.CREATED, runAt, null, 0, null, now, now, jobSettings, JobProgress(0))
        val (idRow, idValue) = if (supportsSerialId) "" to "" else "id, " to "NULL, "
        val id = handle.createUpdate(
            """
                INSERT INTO $jobTable ($idRow status, runAt, statusMessage, retries, kjobId, createdAt, updatedAt, jobId, name, properties, step, max, startedAt, completedAt)
                VALUES ($idValue :status, :runAt, NULL, 0, NULL, :createdAt, :updatedAt, :jobId, :name, :properties, 0, NULL, NULL, NULL)
            """.trimIndent()
        ).bind("status", sj.status.name)
            .bind("createdAt", sj.createdAt.toEpochMilli())
            .bind("updatedAt", sj.updatedAt.toEpochMilli())
            .bind("jobId", sj.settings.id)
            .bind("name", sj.settings.name)
            .bindOrNull("properties", sj.settings.properties.stringify())
            .bindOrNull("runAt", sj.runAt?.toEpochMilli())
            .executeAndReturnGeneratedKeys("id")
            .mapTo(Long::class.java)
            .one()
        return sj.copy(id = id.toString())
    }

    override suspend fun get(id: String): ScheduledJob? {
        val handle = handleProvider()
        return handle.createQuery("SELECT * FROM $jobTable WHERE id = :id")
            .bind("id", id.toLong())
            .map { row -> row.toScheduledJob() }
            .singleOrNull()
    }

    override suspend fun update(
        id: String,
        oldKjobId: UUID?,
        kjobId: UUID?,
        status: JobStatus,
        statusMessage: String?,
        retries: Int
    ): Boolean {
        val handle = handleProvider()
        val kjobIdFilter = if (oldKjobId == null) "AND kjobId IS NULL" else "AND kjobId = :oldKjobId"
        return handle.createUpdate(
            """
                UPDATE $jobTable
                SET status = :status,
                    statusMessage = :statusMessage,
                    retries = :retries,
                    kjobId = :newKjobId,
                    updatedAt = :updatedAt
                WHERE id = :id $kjobIdFilter
            """.trimIndent()
        ).bind("status", status.name)
            .bind("retries", retries)
            .bind("updatedAt", Instant.now(clock).toEpochMilli())
            .bind("id", id.toLong())
            .bindOrNull("statusMessage", statusMessage)
            .bindOrNull("newKjobId", kjobId?.toString())
            .apply {
                if (oldKjobId != null) {
                    bind("oldKjobId", oldKjobId.toString())
                }
            }
            .execute() == 1
    }

    override suspend fun reset(id: String, oldKjobId: UUID?): Boolean {
        val handle = handleProvider()
        val kjobIdFilter = if (oldKjobId == null) "AND kjobId IS NULL" else "AND kjobId = :oldKjobId"
        return handle.createUpdate(
            """
                UPDATE $jobTable
                SET status = :status,
                    statusMessage = NULL,
                    kjobId = NULL,
                    step = 0,
                    max = NULL,
                    startedAt = NULL,
                    completedAt = NULL,
                    updatedAt = :updatedAt
                WHERE id = :id $kjobIdFilter
            """.trimIndent()
        ).bind("status", JobStatus.CREATED.name)
            .bind("updatedAt", Instant.now(clock).toEpochMilli())
            .bind("id", id.toLong())
            .run {
                if (oldKjobId != null) {
                    bind("oldKjobId", oldKjobId.toString())
                } else {
                    this
                }
            }
            .execute() == 1
    }

    override suspend fun startProgress(id: String): Boolean {
        val handle = handleProvider()
        val now = Instant.now(clock).toEpochMilli()
        return handle.createUpdate("UPDATE $jobTable SET startedAt = :startedAt, updatedAt = :updatedAt WHERE id = :id")
            .bind("startedAt", now)
            .bind("updatedAt", now)
            .bind("id", id.toLong())
            .execute() == 1
    }

    override suspend fun completeProgress(id: String): Boolean {
        val handle = handleProvider()
        val now = Instant.now(clock).toEpochMilli()
        return handle.createUpdate("UPDATE $jobTable SET completedAt = :completedAt, updatedAt = :updatedAt WHERE id = :id")
            .bind("completedAt", now)
            .bind("updatedAt", now)
            .bind("id", id.toLong())
            .execute() == 1
    }

    override suspend fun stepProgress(id: String, step: Long): Boolean {
        val handle = handleProvider()
        val now = Instant.now(clock).toEpochMilli()
        return handle.createUpdate(
            """
            UPDATE $jobTable SET 
              step = COALESCE(step, 0) + :step, 
              updatedAt = :updatedAt 
            WHERE id = :id;
        """.trimIndent()
        )
            .bind("step", step)
            .bind("updatedAt", now)
            .bind("id", id.toLong())
            .execute() == 1
    }

    override suspend fun setProgressMax(id: String, max: Long): Boolean {
        val handle = handleProvider()
        val now = Instant.now(clock).toEpochMilli()
        return handle.createUpdate("UPDATE $jobTable SET max = :max, updatedAt = :updatedAt WHERE id = :id")
            .bind("max", max)
            .bind("updatedAt", now)
            .bind("id", id.toLong())
            .execute() == 1
    }

    override suspend fun findNext(
        names: Set<String>,
        status: Set<JobStatus>,
        limit: Int
    ): Flow<ScheduledJob> {
        val handle = handleProvider()
        val namesFilter = if (names.isEmpty()) "" else "AND name IN (<names>)"
        return handle.createQuery("SELECT * FROM $jobTable WHERE status IN (<status>) $namesFilter LIMIT :limit")
            .bindList("status", status.map(JobStatus::name))
            .bind("limit", limit)
            .apply {
                if (namesFilter.isNotBlank()) {
                    bindList("names", names.toList())
                }
            }
            .map { row -> row.toScheduledJob() }
            .asFlow()
    }

    internal fun deleteAll() {
        val handle = handleProvider()
        handle.execute("DELETE FROM $jobTable")
    }

    internal fun dropTables() {
        val handle = handleProvider()
        runCatching { handle.execute("DROP TABLE ${jobTable};") }
    }

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any>.stringify(): String? {
        if (isEmpty()) return null
        val jsonObject = JsonObject(
            mapValues { (_, value) ->
                when (value) {
                    is List<*> -> {
                        if (value.isEmpty()) {
                            buildJsonObject {
                                put("t", "s")
                                putJsonArray("v") {}
                            }
                        } else {
                            val (t, values) = when (val item = value.first()) {
                                is Double -> "d" to (value as List<Double>).map(::JsonPrimitive)
                                is Long -> "l" to (value as List<Long>).map(::JsonPrimitive)
                                is Int -> "i" to (value as List<Int>).map(::JsonPrimitive)
                                is String -> "s" to (value as List<String>).map(::JsonPrimitive)
                                is Boolean -> "b" to (value as List<Boolean>).map(::JsonPrimitive)
                                else -> error("Cannot serialize unsupported list property value: $item")
                            }
                            buildJsonObject {
                                put("t", t)
                                put("v", JsonArray(values))
                            }
                        }
                    }

                    is Double -> JsonPrimitive("d:$value")
                    is Long -> JsonPrimitive("l:$value")
                    is Int -> JsonPrimitive("i:$value")
                    is String -> JsonPrimitive("s:$value")
                    is Boolean -> JsonPrimitive("b:$value")
                    else -> error("Cannot serialize unsupported property value: $value")
                }
            }
        )
        return Json.encodeToString(jsonObject)
    }

    private fun RowView.toScheduledJob(): ScheduledJob {
        return ScheduledJob(
            id = getColumn("id", Long::class.javaObjectType).toString(),
            status = JobStatus.valueOf(getColumn("status", String::class.java)),
            runAt = getColumnOrNull("runAt"),
            statusMessage = getColumn("statusMessage", String::class.java),
            retries = getColumn("retries", Int::class.javaObjectType),
            kjobId = getColumn("kjobId", String::class.java)?.run(UUID::fromString),
            createdAt = getColumn("createdAt"),
            updatedAt = getColumn("updatedAt"),
            settings = JobSettings(
                id = getColumn("jobId", String::class.java),
                name = getColumn("name", String::class.java),
                properties = getColumn("properties", String::class.java)?.parseJsonMap() ?: emptyMap()
            ),
            progress = JobProgress(
                step = getColumn("step", Long::class.javaObjectType),
                max = getColumn("max", Long::class.javaObjectType),
                startedAt = getColumnOrNull("startedAt"),
                completedAt = getColumnOrNull("completedAt")
            )
        )
    }

    private fun RowView.getColumn(name: String): Instant {
        return checkNotNull(getColumnOrNull(name))
    }

    private fun RowView.getColumnOrNull(name: String): Instant? {
        return getColumn(name, Long::class.javaObjectType)?.run(Instant::ofEpochMilli)
    }

    private fun String.parseJsonMap(): Map<String, Any> {
        return Json.decodeFromString<JsonElement>(this)
            .jsonObject
            .mapValues { (_, el) ->
                if (el is JsonObject) {
                    val t = el.getValue("t").jsonPrimitive.content
                    val value = el.getValue("v").jsonArray
                    when (t) {
                        "s" -> value.map { it.jsonPrimitive.content }
                        "d" -> value.map { it.jsonPrimitive.double }
                        "l" -> value.map { it.jsonPrimitive.long }
                        "i" -> value.map { it.jsonPrimitive.int }
                        "b" -> value.map { it.jsonPrimitive.boolean }
                        else -> error("Unknown type prefix '$t'")
                    }.toList()
                } else {
                    val content = el.jsonPrimitive.content
                    val t = content.substringBefore(':')
                    val value = content.substringAfter(':')
                    when (t) {
                        "s" -> value
                        "d" -> value.toDouble()
                        "l" -> value.toLong()
                        "i" -> value.toInt()
                        "b" -> value.toBoolean()
                        else -> error("Unknown type prefix '$t'")
                    }
                }
            }
    }
}

internal fun <T> Update.bindOrNull(column: String, value: T?): Update {
    return if (value == null) bindNull(column, Types.NULL) else bind(column, value)
}
