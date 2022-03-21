package kjob.dashboard.components

import androidx.compose.runtime.Composable
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text


@Composable
fun LoadingIndicator() {
    Div({ classes("d-flex", "justify-content-center", "align-items-center", "w-100", "h-100") }) {
        Div({ classes("spinner-border", "text-light") }) {
            Span({ classes("visually-hidden") }) { Text("Loading...") }
        }
    }
}