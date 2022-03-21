package kjob.dashboard.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kjob.dashboard.models.Job
import kjob.dashboard.models.JobStatus
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.periodUntil
import org.jetbrains.compose.web.css.height
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.width
import org.jetbrains.compose.web.dom.A
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import kotlin.math.absoluteValue


@Composable
fun JobCard(job: Job) {
    Div({
        classes("p-2")
        style {
            width(300.px)
            height(300.px)
        }
    }) {
        Div({ classes("d-flex", "flex-column", "gap-2", "p-3", "bg-dark", "rounded", "h-100") }) {
            Div({ classes("d-flex", "justify-content-between") }) {
                Div({ classes("d-flex", "align-items-center", "overflow-hidden") }) {
                    val jobId = remember(job) { job.settings.id.split("-").first() }
                    Span({ classes("d-inline-block", "text-truncate", "font-monospace", "fw-bold", "fs-6") }) {
                        Text(jobId)
                    }
                }
                Div({ classes("d-flex", "align-items-center") }) {
                    val color = remember(job.status) {
                        when (job.status) {
                            JobStatus.COMPLETE -> "bg-success"
                            JobStatus.RUNNING -> "bg-primary"
                            JobStatus.FAILED, JobStatus.ERROR -> "bg-danger"
                            JobStatus.CREATED, JobStatus.SCHEDULED -> "bg-secondary"
                        }
                    }
                    Span({ classes("badge", "rounded-pill", color) }) { Text(job.status.name) }
                }
            }
            Div({ classes("d-flex") }) {
            }
            Div({ classes("d-flex", "justify-content-between", "mt-auto")}) {
                Div({ classes("d-inline-block", "text-truncate", "me-2") }) {
                    if (job.kjobId != null) {
                        A(href = null) {
                            Text(job.kjobId.split("-").first())
                        }
                    }
                }
                Div({ classes("d-inline-block", "text-truncate", "align-self-end", "mt-auto", "fs-6") }) {
                    val timeSince = remember(job) {
                        job.progress.completedAt?.durationString("Completed ", " ago")
                            ?: job.progress.startedAt?.durationString("Started ", " ago")
                            ?: job.runAt?.durationString("Runs in ", "")
                    }
                    if (timeSince != null) {
                        Text(timeSince)
                    }
                }
            }
        }
    }
}

private fun Instant.durationString(prefix: String = "", suffix: String = ""): String {
    val timeSince = periodUntil(Clock.System.now(), TimeZone.UTC)
    return buildString(prefix.length + suffix.length) {
        fun appendTime(value: Int, name: String) {
            append(value)
            append(" ")
            append(name)
            if (value > 1) append("s")
        }
        append(prefix)
        val years = timeSince.years.absoluteValue
        val months = timeSince.months.absoluteValue
        val days = timeSince.days.absoluteValue
        val hours = timeSince.hours.absoluteValue
        val minutes = timeSince.minutes.absoluteValue
        val seconds = timeSince.seconds.absoluteValue
        when {
            years > 0 -> appendTime(years, "year")
            months > 0 -> appendTime(months, "month")
            days > 0 -> appendTime(days, "days")
            hours > 0 -> appendTime(hours, "hour")
            minutes > 0 -> appendTime(minutes, "minute")
            seconds > 0 -> appendTime(seconds, "second")
        }
        append(suffix)
    }
}