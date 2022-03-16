package kjob.jdbi.repository

import kjob.core.job.Lock
import kjob.core.repository.LockRepository
import kjob.jdbi.JdbiKJob
import org.jdbi.v3.core.Handle
import java.time.Clock
import java.time.Instant
import java.util.*
import kotlin.time.Duration.Companion.minutes

internal class JdbiLockRepository(
    private val handleProvider: () -> Handle,
    config: JdbiKJob.Configuration,
    private val clock: Clock,
) : LockRepository {

    constructor(handle: Handle, clock: Clock, conf: JdbiKJob.Configuration.() -> Unit)
            : this({ handle }, JdbiKJob.Configuration().apply(conf), clock)

    private val lockTable = config.lockTableName
    private val ttl = config.expireLockInMinutes.minutes

    fun createTable() {
        val handle = handleProvider()
        val result = handle.execute(
            """
            CREATE TABLE IF NOT EXISTS $lockTable (
                id          CHAR(36) PRIMARY KEY NOT NULL,
                updatedAt   INTEGER  NOT NULL,
                expiresAt   INTEGER  NOT NULL
            );
            """.trimIndent()
        )
        check(result == 0 || result == 1) {
            "Failed to create lock table with name '$lockTable': result == $result"
        }
    }

    override suspend fun ping(id: UUID): Lock {
        val handle = handleProvider()
        val now = Instant.now(clock)
        val expiresAt = now.plusSeconds(ttl.inWholeSeconds)
        val lock = Lock(id, now)
        handle.createUpdate("INSERT OR REPLACE INTO $lockTable VALUES (:id, :updatedAt, :expiresAt)")
            .bind("id", id.toString())
            .bind("updatedAt", now.toEpochMilli())
            .bind("expiresAt", expiresAt.toEpochMilli())
            .execute()
        return lock
    }

    override suspend fun exists(id: UUID): Boolean {
        val handle = handleProvider()
        return handle.createQuery("SELECT COUNT(id) FROM $lockTable WHERE id = :id AND :now < expiresAt")
            .bind("id", id)
            .bind("now", Instant.now(clock).toEpochMilli())
            .mapTo(Int::class.java)
            .one() == 1
    }

    fun clearExpired() {
        val handle = handleProvider()
        handle.createUpdate("DELETE FROM $lockTable WHERE :now < expiresAt")
            .bind("now", Instant.now(clock).toEpochMilli())
            .execute()
    }

    internal fun deleteAll() {
        val handle = handleProvider()
        handle.execute("DELETE FROM $lockTable")
    }
}