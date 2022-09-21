package kjob.mongo.repository

import io.kotest.matchers.shouldBe
import io.kotest.provided.ProjectConfig
import kjob.core.repository.LockRepository
import kjob.core.repository.LockRepositoryContract
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.runBlocking

class MongoLockRepositorySpec : LockRepositoryContract() {
    private val mongoClient = ProjectConfig.newMongoClient()

    override val testee: LockRepository = MongoLockRepository(mongoClient, clock) {
        databaseName = "test-" + id()
        client = mongoClient
        expireLockInMinutes = 5
    }

    private val mongoTestee = testee as MongoLockRepository

    override suspend fun deleteAll() {
        mongoTestee.deleteAll()
    }

    init {
        beforeSpec {
            runBlocking { mongoTestee.ensureIndexes() }
        }
        should("return applied ttl") {
            val ttlIndex = mongoClient
                .getDatabase(mongoTestee.conf.databaseName)
                .getCollection(mongoTestee.conf.lockCollection)
                .listIndexes()
                .asFlow()
                .first { it.getString("name") == "updated_at_ttl" }
            ttlIndex.getLong("expireAfterSeconds") shouldBe 300
        }
    }
}
