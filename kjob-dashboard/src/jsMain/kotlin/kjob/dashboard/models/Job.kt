package kjob.dashboard.models

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class Job(
    val id: String,
    val statusMessage: String?,
    val retries: Int,
    val kjobId: String?,
    val status: JobStatus,
    val updatedAt: Instant,
    val createdAt: Instant,
    val runAt: Instant?,
    val settings: Settings,
    val progress: Progress,
) {
    @Serializable
    data class Settings(
        val id: String,
        val name: String,
        val props: Map<String, JsonElement>,
    )

    @Serializable
    data class Progress(
        val max: Int?,
        val step: Int,
        val startedAt: Instant?,
        val completedAt: Instant?,
    )
}
