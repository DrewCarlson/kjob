package kjob.inmem.repository

import io.kotest.assertions.throwables.shouldThrow
import kjob.core.internal.scheduler.js
import kjob.core.repository.JobRepository
import kjob.core.repository.JobRepositoryContract
import java.util.*

class InMemJobRepositorySpec : JobRepositoryContract() {

    override val testee: JobRepository = InMemJobRepository(clock)

    override fun randomJobId(): String = UUID.randomUUID().toString()

    private val inmemTestee = testee as InMemJobRepository

    override suspend fun deleteAll() {
        inmemTestee.deleteAll()
    }

    init {
        should("only allow unique job ids") {
            val job = js()

            testee.save(job, null)
            shouldThrow<IllegalArgumentException> {
                testee.save(job, null)
            }
        }

    }

}