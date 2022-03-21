package kjob.dashboard

import androidx.compose.runtime.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kjob.dashboard.components.JobCard
import kjob.dashboard.components.LoadingIndicator
import kjob.dashboard.components.SideBar
import kjob.dashboard.components.VirtualScroller
import kjob.dashboard.models.Job
import kjob.dashboard.models.JobStatus
import kjob.dashboard.models.JobType
import kjob.dashboard.models.Stats
import kotlinx.browser.window
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.renderComposable
import org.w3c.dom.url.URL

private val DEFAULT_JOB_STATUS = JobStatus.SCHEDULED
private val url = MutableStateFlow(URL(window.location.href))

fun setFilteredUrl(jobNameFilter: String?, statusFilter: JobStatus?) {
    val newUrl = URL(window.location.href).apply {
        searchParams.apply {
            if (jobNameFilter == null) delete("job") else set("job", jobNameFilter)
            if (statusFilter == null) delete("status") else set("status", statusFilter.name)
        }
    }
    window.history.pushState(null, "", newUrl.href)
    url.value = newUrl
}

fun main() {
    kotlinext.js.require("bootstrap/dist/js/bootstrap.min.js")
    kotlinext.js.require("bootstrap/dist/css/bootstrap.min.css")
    kotlinext.js.require("bootstrap-icons/font/bootstrap-icons.css")
    kotlinext.js.require("@fontsource/open-sans/index.css")
    val http = HttpClient { install(ContentNegotiation) { json() } }
    renderComposable(rootElementId = "root") {
        val currentUrl by url.collectAsState()
        val jobAndStatus by derivedStateOf {
            currentUrl.searchParams.run {
                get("job") to (get("status")?.run(JobStatus::valueOf) ?: DEFAULT_JOB_STATUS)
            }
        }
        LaunchedEffect(Unit) {
            // If we use the default filter, update the browser's url.
            if (jobAndStatus.second == DEFAULT_JOB_STATUS && !currentUrl.searchParams.has("status")) {
                setFilteredUrl(null, DEFAULT_JOB_STATUS)
            }
        }
        val stats by produceState<Stats?>(null, jobAndStatus.first, jobAndStatus.second) {
            while (isActive) {
                try {
                    value = http.get("/kjob/stats").body<Stats>()
                } catch (e: Throwable) {
                    println("Failed to load stats: ${e.message ?: e.cause?.message}")
                }
                delay(1000)
            }
        }
        val jobTypes: List<JobType> by produceState(emptyList()) {
            try {
                value = http.get("/kjob/job-types").body()
            } catch (e: Throwable) {
                println("Failed to load job types: ${e.message ?: e.cause?.message}")
            }
        }
        val jobs by produceState<List<Job>?>(null, jobAndStatus) {
            value = null
            try {
                value = http.get("/kjob/jobs") {
                    jobAndStatus.first?.let { name ->
                        url.parameters["names"] = name
                    }
                    jobAndStatus.second.let { status ->
                        url.parameters["status"] = status.name
                    }
                }.body<List<Job>>()
            } catch (e: Throwable) {
                println("Failed to load jobs: ${e.message ?: e.cause?.message}")
            }
        }

        Div({ id("main") }) {
            Div({ classes("d-flex", "flex-row", "h-100", "w-100") }) {
                SideBar(
                    stats = stats,
                    jobTypes = jobTypes,
                )
                Div({ classes("w-100", "h-100") }) {
                    jobs.also { jobs ->
                        if (jobs == null) {
                            LoadingIndicator()
                        } else if (jobs.isNotEmpty()) {
                            VirtualScroller(jobs) { job ->
                                JobCard(job)
                            }
                        } else {
                            NoJobs()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NoJobs() {
    Div({ classes("d-flex", "justify-content-center", "align-items-center", "h-100") }) {
        Div({ classes("rounded", "bg-dark", "p-4") }) {
            Div({ classes("fs-3", "text-light") }) {
                Text("No Jobs")
            }
        }
    }
}
