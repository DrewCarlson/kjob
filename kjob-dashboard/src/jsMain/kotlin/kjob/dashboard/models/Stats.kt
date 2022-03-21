package kjob.dashboard.models

import kotlinx.serialization.Serializable

@Serializable
data class Stats(
    val instances: Int,
    val all: Map<JobStatus, Int>,
    val jobs: Map<String, Map<JobStatus, Int>>,
)
