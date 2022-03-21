package kjob.dashboard.models

import kotlinx.serialization.Serializable

@Serializable
enum class JobStatus {
    CREATED,
    SCHEDULED,
    RUNNING,
    COMPLETE,
    ERROR,
    FAILED
}