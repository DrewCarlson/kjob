package kjob.mongo.repository

import io.kotest.matchers.shouldBe
import io.kotest.provided.ProjectConfig
import kjob.core.repository.JobRepository
import kjob.core.repository.JobRepositoryContract
import kjob.mongo.repository.structure.JobSettingsStructure
import kjob.mongo.repository.structure.ScheduledJobStructure
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.runBlocking
import org.bson.Document
import org.bson.types.ObjectId

class MongoJobRepositorySpec : JobRepositoryContract() {
    private val mongoClient = ProjectConfig.newMongoClient()

    override val testee: JobRepository = MongoJobRepository(mongoClient, clock) {
        databaseName = "test-" + id()
        client = mongoClient
    }

    private val mongoTestee = testee as MongoJobRepository

    override suspend fun deleteAll() {
        mongoTestee.deleteAll()
    }

    override fun randomJobId(): String = ObjectId.get().toHexString()

    init {
        beforeSpec {
            runBlocking { mongoTestee.ensureIndexes() }
        }

        should("ensure index") {
            val unique = mongoClient
                .getDatabase(mongoTestee.conf.databaseName)
                .getCollection(mongoTestee.conf.jobCollection)
                .listIndexes()
                .asFlow()
                .first { it.getString("name") == "unique_job_id" }
            unique.getBoolean("unique") shouldBe true
            val expectedKey = Document().append("${ScheduledJobStructure.SETTINGS.key}.${JobSettingsStructure.ID.key}", 1)
            unique.get("key", Document::javaClass) shouldBe expectedKey
        }
    }
}
