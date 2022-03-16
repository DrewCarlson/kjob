package kjob.api

import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kjob.core.KJob
import kjob.core.job.JobStatus

fun Application.installKJobApi(
    kjobInstance: KJob,
    rootRoute: Route? = null,
    installSerialization: Boolean = true,
) {
    installKJobApi(
        listOf(kjobInstance),
        rootRoute,
        installSerialization
    )
}

fun Application.installKJobApi(
    kjobInstances: List<KJob>,
    rootRoute: Route? = null,
    installSerialization: Boolean = true,
) {
    if (installSerialization) {
        install(ContentNegotiation) {
            json()
        }
    }

    if (rootRoute == null) {
        routing { installKJobApiRoutes(kjobInstances) }
    } else {
        rootRoute.installKJobApiRoutes(kjobInstances)
    }
}

private fun Route.installKJobApiRoutes(
    kjobInstances: List<KJob>,
) {
    val jobStatuses = JobStatus.values().toList()
    val extensions = kjobInstances.map { it(KjobApiExtension) }
    val uniqueDatabaseExtensions = mutableListOf<KjobApiEx>()
    extensions.forEach { extension ->
        if (uniqueDatabaseExtensions.none(extension::shareDatabase)) {
            uniqueDatabaseExtensions.add(extension)
        }
    }
    route("/kjob") {
        get("/statuses") {
            call.respond(jobStatuses)
        }
        get("/job-types") {
            call.respond(extensions.flatMap(KjobApiEx::jobTypes))
        }
        route("/jobs") {
            get {
                val limit = call.request.queryParameters["limit"]?.toIntOrNull()
                val filterNames = call.request.queryParameters["names"]?.split(",")?.toSet().orEmpty()
                val filterStatuses = call.request.queryParameters["status"]
                    ?.split(",")
                    ?.mapNotNull { runCatching { JobStatus.valueOf(it) }.getOrNull() }
                    ?.toSet()
                call.respond(uniqueDatabaseExtensions.flatMap { extension ->
                    extension.jobs(filterNames, filterStatuses, limit)
                })
            }
            get("/{id}") {
                val id = call.parameters["id"]
                val instanceId = call.parameters["instanceId"]
                if (id == null || (instanceId != null && extensions.none { it.instanceId == instanceId })) {
                    return@get call.respond(NotFound)
                }
                val filteredExtensions = if (instanceId == null) {
                    uniqueDatabaseExtensions
                } else {
                    extensions.filter { it.instanceId == instanceId }
                }
                call.respond(filteredExtensions.mapNotNull { it.job(id) })
            }
        }
        route("/instances") {
            get {
                call.respond(extensions.map(KjobApiEx::instance))
            }

            get("/{id}") {
                val id = call.parameters["id"] ?: return@get call.respond(NotFound)
                val instance = extensions.find { it.instanceId == id } ?: return@get call.respond(NotFound)
                call.respond(instance)
            }
        }
    }
}
