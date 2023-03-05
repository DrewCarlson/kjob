package kjob.jdbi.repository

import io.kotest.matchers.shouldBe
import kjob.core.repository.LockRepository
import kjob.core.repository.LockRepositoryContract
import kjob.core.repository.now
import org.jdbi.v3.core.Handle
import org.jdbi.v3.core.Jdbi
import org.jdbi.v3.core.statement.Slf4JSqlLogger

class JdbiPostgresLockRepositorySpec : JdbiLockRepositorySpec(
    handle = Jdbi.create("jdbc:postgresql:?user=postgres&password=password").apply {
        setSqlLogger(Slf4JSqlLogger())
    }.open()
)

class JdbiMysqlLockRepositorySpec : JdbiLockRepositorySpec(
    handle = Jdbi.create("jdbc:mysql://root:password@localhost/test").apply {
        setSqlLogger(Slf4JSqlLogger())
    }.open()
)

class JdbiSqliteLockRepositorySpec : JdbiLockRepositorySpec(
    handle = Jdbi.create("jdbc:sqlite::memory:").apply {
        setSqlLogger(Slf4JSqlLogger())
    }.open()
)

abstract class JdbiLockRepositorySpec(
    private val handle: Handle
) : LockRepositoryContract() {

    override val testee: LockRepository = JdbiLockRepository(handle, clock) {
        handle = this@JdbiLockRepositorySpec.handle
        expireLockInMinutes = 1
    }

    private val jdbiTestee = testee as JdbiLockRepository

    override suspend fun deleteAll() {
        jdbiTestee.deleteAll()
    }

    init {
        beforeSpec {
            jdbiTestee.createTable()
        }
        afterSpec {
            jdbiTestee.dropTables()
            handle.close()
        }
        should("not select expired records") {
            val id = id()
            jdbiTestee.ping(id)
            clock.update(now().plusSeconds(60))
            jdbiTestee.exists(id) shouldBe false
        }
    }
}
