package kjob.jdbi.repository

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kjob.core.job.JobSettings
import kjob.core.repository.JobRepository
import kjob.core.repository.JobRepositoryContract
import org.jdbi.v3.core.Handle
import org.jdbi.v3.core.Jdbi
import org.jdbi.v3.core.statement.Slf4JSqlLogger

class JdbiPostgresJobRepositorySpec : JdbiJobRepositorySpec(
    handle = Jdbi.create("jdbc:postgresql:?user=postgres&password=password").apply {
        setSqlLogger(Slf4JSqlLogger())
    }.open()
)

class JdbiMysqlJobRepositorySpec : JdbiJobRepositorySpec(
    handle = Jdbi.create("jdbc:mysql://root:password@localhost/test").apply {
        setSqlLogger(Slf4JSqlLogger())
    }.open()
)

class JdbiSqliteJobRepositorySpec : JdbiJobRepositorySpec(
    handle = Jdbi.create("jdbc:sqlite::memory:").apply {
        setSqlLogger(Slf4JSqlLogger())
    }.open()
)

abstract class JdbiJobRepositorySpec(
    private val handle: Handle
) : JobRepositoryContract() {

    final override val testee: JobRepository = JdbiJobRepository(handle, clock) {
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
            jdbiTestee.dropTables()
            handle.close()
        }
        should("serialize and deserialize props") {
            val props = mapOf(
                "string" to "test",
                "int" to 0,
                "long" to 0L,
                "double" to 0.0,
                "boolean" to false,
                "stringList" to listOf("hello", "world"),
                "intList" to listOf(0, 1),
                "longList" to listOf(0L, 1L),
                "doubleList" to listOf(0.0, 1.0),
                "booleanList" to listOf(false, true)
            )
            val job = testee.save(JobSettings("test", "test", props), null)
            job.settings.properties shouldBe props
            val jobFromDb = testee.get(job.id)
            jobFromDb shouldNotBe null
            jobFromDb?.settings?.properties shouldBe props
        }
    }
}
