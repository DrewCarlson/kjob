package kjob.core.job

import java.time.Instant
import java.util.*

data class Lock(val id: UUID, val updatedAt: Instant)