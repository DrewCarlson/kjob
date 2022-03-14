package kjob.core.repository

import kjob.core.job.Lock
import java.util.*

interface LockRepository {

    suspend fun ping(id: UUID): Lock

    suspend fun exists(id: UUID): Boolean
}