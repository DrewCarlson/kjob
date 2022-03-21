package kjob.dashboard.components

import androidx.compose.runtime.*
import kjob.dashboard.models.JobStatus
import kjob.dashboard.models.JobType
import kjob.dashboard.models.Stats
import kjob.dashboard.setFilteredUrl
import org.jetbrains.compose.web.attributes.AttrsScope
import org.jetbrains.compose.web.css.cursor
import org.jetbrains.compose.web.css.overflowY
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.width
import org.jetbrains.compose.web.dom.*
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLSpanElement
import kotlin.random.Random


@Composable
fun SideBar(
    stats: Stats?,
    jobTypes: List<JobType>,
) {
    Div({
        classes(
            "d-flex", "flex-column", "flex-shrink-0",
            "m-2", "p-2", "bg-dark", "rounded"
        )
        style {
            width(240.px)
        }
    }) {
        Span({ classes("fs-4") }) { Text("KJob Dashboard") }
        Hr()
        Div({
            classes("h-100", "p-1")
            style {
                overflowY("scroll")
                property("scrollbar-width", "thin")
            }
        }) {
            Ul({ classes("list-unstyled") }) {
                MenuItem(
                    buttonText = "Instances",
                    badgeText = stats?.instances?.toString(),
                    badgeAttrs = { classes("bg-info") },
                )
                val allJobStatuses = remember { JobStatus.values() }
                allJobStatuses.forEach { status ->
                    MenuItem(
                        buttonText = status.name.lowercase(),
                        badgeText = stats?.all?.get(status)?.toString() ?: "0",
                        buttonAttrs = {
                            onClick {
                                setFilteredUrl(null, status)
                            }
                        }
                    )
                }
                jobTypes.forEach { jobType ->
                    MenuItem(buttonText = jobType.name) {
                        allJobStatuses.forEach { status ->
                            Li {
                                Div({ classes("d-flex", "justify-content-between") }) {
                                    A(null, {
                                        classes("link-light")
                                        style { cursor("pointer") }
                                        onClick { setFilteredUrl(jobType.name, status) }
                                    }) {
                                        Text(status.name.lowercase())
                                    }
                                    Div({ classes("d-flex", "align-items-center") }) {
                                        Span({ classes("badge", "rounded-pill", "bg-secondary") }) {
                                            Text(stats?.jobs?.get(jobType.name)?.get(status)?.toString() ?: "0")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuItem(
    buttonText: String,
    buttonAttrs: (AttrsScope<HTMLButtonElement>.() -> Unit)? = null,
    badgeText: String? = null,
    badgeAttrs: (AttrsScope<HTMLSpanElement>.() -> Unit)? = null,
    collapsibleMenuBody: @Composable (() -> Unit)? = null,
) {
    val menuCollapseId = remember(buttonText) { "menu-item-${Random.nextLong(0, Long.MAX_VALUE)}" }
    Li({ classes("mb-1", "me-1") }) {
        Div({ classes("d-flex", "justify-content-between") }) {
            Div({ classes("d-flex", "align-items-center") }) {
                var isExpanded by remember { mutableStateOf(false) }
                Button({
                    buttonAttrs?.invoke(this)
                    classes("btn", "rounded", "text-light")
                    if (collapsibleMenuBody != null) {
                        classes("btn-toggle")
                        attr("data-bs-toggle", "collapse")
                        attr("data-bs-target", "#$menuCollapseId")
                        attr("aria-expanded", "false")
                    }
                    onClick { isExpanded = !isExpanded }
                }) {
                    Text(buttonText)
                    if (collapsibleMenuBody != null) {
                        I({
                            classes("bi", "text-light", "px-1")
                            if (isExpanded) classes("bi-chevron-down") else classes("bi-chevron-right")
                        })
                    }
                }
            }
            Div({ classes("d-flex", "align-items-center") }) {
                if (badgeText != null) {
                    Span({
                        classes("badge", "rounded-pill", "bg-secondary")
                        badgeAttrs?.invoke(this)
                    }) {
                        Text(badgeText)
                    }
                }
            }
        }
        if (collapsibleMenuBody != null) {
            Div({
                id(menuCollapseId)
                classes("collapse")
            }) {
                Ul({ classes("btn-toggle-nav", "list-unstyled") }) {
                    collapsibleMenuBody()
                }
            }
        }
    }
}
