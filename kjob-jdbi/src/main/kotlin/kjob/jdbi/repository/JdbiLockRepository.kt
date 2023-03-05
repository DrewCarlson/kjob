package kjob.jdbi.repository

import kjob.core.job.Lock
import kjob.core.repository.LockRepository
import kjob.jdbi.JdbiKJob
import org.jdbi.v3.core.Handle
import org.jdbi.v3.core.JdbiException
import java.time.Clock
import java.time.Instant
import java.util.*
import kotlin.time.Duration.Companion.minutes

internal class JdbiLockRepository(
    private val handleProvider: () -> Handle,
    config: JdbiKJob.Configuration,
    private val clock: Clock
) : LockRepository {

    constructor(handle: Handle, clock: Clock, conf: JdbiKJob.Configuration.() -> Unit) :
        this({ handle }, JdbiKJob.Configuration().apply(conf), clock)

    private val lockTable = config.lockTableName
    private val ttl = config.expireLockInMinutes.minutes
    private var mysqlUpsert = false

    fun createTable() {
        val handle = handleProvider()
        mysqlUpsert = try {
            handle.execute("SELECT @@VERSION_COMMENT;")
            true
        } catch (e: JdbiException) {
            false
        }
        val result = handle.execute(
            """
            CREATE TABLE IF NOT EXISTS $lockTable (
                id          CHAR(36) PRIMARY KEY NOT NULL,
                updatedAt   BIGINT  NOT NULL,
                expiresAt   BIGINT  NOT NULL
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
        if (mysqlUpsert) {
            handle.createUpdate("""
                INSERT INTO $lockTable
                VALUES (:id, :updatedAt, :expiresAt)
                ON DUPLICATE KEY UPDATE
                    updatedAt = VALUES(updatedAt),
                    expiresAt = VALUES(expiresAt);
            """.trimIndent())
        } else {
            handle.createUpdate("""
                INSERT INTO $lockTable
                VALUES (:id, :updatedAt, :expiresAt)
                ON CONFLICT (id) DO UPDATE SET
                    updatedAt = excluded.updatedAt,
                    expiresAt = excluded.expiresAt;
            """.trimIndent())
        }
            .bind("id", id.toString())
            .bind("updatedAt", now.toEpochMilli())
            .bind("expiresAt", expiresAt.toEpochMilli())
            .execute()
        return lock
    }

    override suspend fun exists(id: UUID): Boolean {
        val handle = handleProvider()
        return handle.createQuery("SELECT COUNT(id) FROM $lockTable WHERE id = :id AND :now < expiresAt")
            .bind("id", id.toString())
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

    internal fun dropTables() {
        val handle = handleProvider()
        handle.execute("DROP TABLE ${lockTable};")
    }
}
