package kjob.inmem.repository

import kjob.core.job.Lock
import kjob.core.repository.LockRepository
import kjob.inmem.InMemKJob
import java.time.Clock
import java.time.Instant
import java.util.*
import java.util.concurrent.ConcurrentHashMap

internal class InMemLockRepository(private val conf: InMemKJob.Configuration, private val clock: Clock) :
    LockRepository {

    constructor(clock: Clock, conf: InMemKJob.Configuration.() -> Unit) :
        this(InMemKJob.Configuration().also(conf), clock)

    private val map = ConcurrentHashMap<UUID, Lock>()

    override suspend fun ping(id: UUID): Lock {
        return checkNotNull(
            map.compute(id) { _, _ ->
                Lock(id, Instant.now(clock))
            }
        )
    }

    override suspend fun exists(id: UUID): Boolean {
        val lock = map[id]
        return lock == null || lock.updatedAt.plusSeconds(conf.expireLockInMinutes * 60).isAfter(Instant.now(clock))
    }

    internal fun deleteAll() {
        map.clear()
    }
}
