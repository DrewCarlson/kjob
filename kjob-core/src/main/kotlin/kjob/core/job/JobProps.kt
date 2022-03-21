package kjob.core.job

import kjob.core.Job
import kjob.core.Prop
import kjob.core.internal.utils.Generated
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

@Suppress("UNCHECKED_CAST")
class JobProps<J : Job> internal constructor(
    private val data: Map<String, Any>,
    private val json: Json,
) {
    inline operator fun <reified T : Any?> get(key: Prop<J, T>): T {
        return if ((key as Prop.Companion.Impl<J, T>).serialize) {
            getData(key.name, serializer())
        } else {
            getData(key.name)
        }
    }

    @PublishedApi
    internal fun <T> getData(keyName: String, serializer: KSerializer<T>? = null): T {
        return if (serializer == null) {
            data[keyName] as T
        } else {
            json.decodeFromString(serializer, data[keyName] as String)
        }
    }

    @Generated
    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || javaClass != other.javaClass) {
            return false
        }
        val props = other as JobProps<J>
        return data == props.data
    }

    @Generated
    override fun hashCode(): Int {
        return data.hashCode()
    }

    @Generated
    override fun toString(): String {
        return "JobProps$data"
    }
}