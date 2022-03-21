package kjob.dashboard.models

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class JobType(
    val name: String,
    val maxRetries: Int,
    val executionType: String,
    val kron: Kron?,
    val propNames: List<String>,
) {
    @Serializable
    data class Kron(
        val expression: String,
        val executionTime: ExecutionTime
    ) {
        @Serializable
        data class ExecutionTime(
            val next: Instant?,
            val previous: Instant?,
            val millisUntilNext: Long?,
        )
    }
}