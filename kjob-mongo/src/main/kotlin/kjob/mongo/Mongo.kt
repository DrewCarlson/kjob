package kjob.mongo

import kjob.core.KJob
import kjob.core.KJobFactory

object Mongo : KJobFactory<MongoKJob, MongoKJob.Configuration> {
    override fun create(configure: MongoKJob.Configuration.() -> Unit): KJob {
        return MongoKJob(MongoKJob.Configuration().apply(configure))
    }
}