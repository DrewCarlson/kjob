package kjob.core

import kotlin.reflect.KClass

interface BaseJob {
    val name: String
}

abstract class KronJob(override val name: String, val cronExpression: String) : BaseJob

abstract class Job(override val name: String) : BaseJob {
    private val propNamesList = mutableListOf<String>()
    val propNames: List<String>
        get() = propNamesList

    @Suppress("UNUSED_PARAMETER")
    protected fun <T : Any, J : Job> J.serializable(name: String, kclass: KClass<T>): Prop<J, T> {
        return Prop(name.also(propNamesList::add), true)
    }

    protected fun <J : Job> J.integer(name: String): Prop<J, Int> = Prop(name.also(propNamesList::add))
    protected fun <J : Job> J.double(name: String): Prop<J, Double> = Prop(name.also(propNamesList::add))
    protected fun <J : Job> J.long(name: String): Prop<J, Long> = Prop(name.also(propNamesList::add))
    protected fun <J : Job> J.bool(name: String): Prop<J, Boolean> = Prop(name.also(propNamesList::add))
    protected fun <J : Job> J.string(name: String): Prop<J, String> = Prop(name.also(propNamesList::add))

    protected fun <J : Job> J.integerList(name: String): Prop<J, List<Int>> = Prop(name.also(propNamesList::add))
    protected fun <J : Job> J.doubleList(name: String): Prop<J, List<Double>> = Prop(name.also(propNamesList::add))
    protected fun <J : Job> J.longList(name: String): Prop<J, List<Long>> = Prop(name.also(propNamesList::add))
    protected fun <J : Job> J.boolList(name: String): Prop<J, List<Boolean>> = Prop(name.also(propNamesList::add))
    protected fun <J : Job> J.stringList(name: String): Prop<J, List<String>> = Prop(name.also(propNamesList::add))


    protected fun <J : Job, T : Any> Prop<J, T>.nullable(): Prop<J, T?> = Prop(name)
}

interface Prop<J : Job, T> {
    val name: String

    companion object {
        data class Impl<J : Job, T>(
            override val name: String,
            val serialize: Boolean
        ) : Prop<J, T>

        operator fun <J : Job, T> invoke(
            name: String,
            serialize: Boolean = false
        ): Prop<J, T> = Impl(name, serialize)
    }
}
