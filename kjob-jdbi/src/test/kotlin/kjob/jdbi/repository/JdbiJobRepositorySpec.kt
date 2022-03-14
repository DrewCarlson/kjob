package kjob.jdbi.repository

import kjob.core.repository.JobRepository
import kjob.core.repository.JobRepositoryContract
import org.jdbi.v3.core.Jdbi
import org.jdbi.v3.core.statement.Slf4JSqlLogger

class JdbiJobRepositorySpec : JobRepositoryContract() {
    private val handle = Jdbi.create("jdbc:sqlite::memory:").apply {
        setSqlLogger(Slf4JSqlLogger())
    }.open()

    override val testee: JobRepository = JdbiJobRepository(handle, clock) {
        handle = this@JdbiJobRepositorySpec.handle
    }

    private val jdbiTestee = testee as JdbiJobRepository

    override suspend fun deleteAll() {
        jdbiTestee.deleteAll()
    }

    override fun randomJobId(): String = System.currentTimeMillis().toString()

    init {
        beforeSpec {
            jdbiTestee.createTable()
        }
        afterSpec {
            handle.close()
        }
    }
}