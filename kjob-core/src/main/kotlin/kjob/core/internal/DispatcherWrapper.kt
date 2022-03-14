package kjob.core.internal

import kotlinx.coroutines.CoroutineDispatcher

interface DispatcherWrapper {

    val coroutineDispatcher: CoroutineDispatcher

    fun canExecute(): Boolean

    fun shutdown()
}