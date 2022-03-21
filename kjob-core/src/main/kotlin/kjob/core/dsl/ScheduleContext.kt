package kjob.core.dsl

import kjob.core.Job
import kjob.core.Prop
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import java.util.*

@JobDslMarker
class ScheduleContext<J : Job>(private val json: Json) {
    /**
     * Override the default unique id generated for this job. Using the same id twice
     * will result in an exception on scheduling. It is recommended to define this to prevent
     * the execution of the same job more than once.
     */
    var jobId: String = UUID.randomUUID().toString()

    inner class Props {
        internal val props = mutableMapOf<String, Any>()

        inline operator fun <reified T> set(key: Prop<J, T>, value: T?) {
            if (value != null) {
                if ((key as Prop.Companion.Impl<J, T>).serialize) {
                    setProp(key.name, value, serializer())
                } else {
                    setProp(key.name, value)
                }
            }
        }

        @PublishedApi
        internal fun <T : Any> setProp(keyName: String, value: T, serializer: KSerializer<T>? = null) {
            return if (serializer == null) {
                props[keyName] = value as Any
            } else {
                props[keyName] = json.encodeToString(serializer, value)
            }
        }
    }

    val props = Props()
}
