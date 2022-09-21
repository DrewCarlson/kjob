package kjob.jdbi.repository

import io.kotest.matchers.shouldBe
import kjob.core.repository.LockRepository
import kjob.core.repository.LockRepositoryContract
import kjob.core.repository.now
import org.jdbi.v3.core.Jdbi
import org.jdbi.v3.core.statement.Slf4JSqlLogger

class JdbiLockRepositorySpec : LockRepositoryContract() {
    private val handle = Jdbi.create("jdbc:sqlite::memory:").apply {
        setSqlLogger(Slf4JSqlLogger())
    }.open()

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
