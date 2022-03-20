package kjob.core

import kjob.core.internal.DispatcherWrapper
import kjob.core.job.JobExecutionType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.*

interface JobExecutors {
    val executorService: ScheduledExecutorService
    val dispatchers: Map<JobExecutionType, DispatcherWrapper>

    fun shutdown() {
        dispatchers.values.forEach { it.shutdown() }
        executorService.shutdown()
    }
}

internal class DefaultJobExecutors(config: KJob.Configuration) : JobExecutors {
    override val executorService: ScheduledExecutorService by lazy { ScheduledThreadPoolExecutor(3) }
    override val dispatchers: Map<JobExecutionType, DispatcherWrapper> = mapOf(
            JobExecutionType.BLOCKING to object : DispatcherWrapper {
                private val executor = Executors.newFixedThreadPool(config.blockingMaxJobs) { r ->
                    Thread(r, "kjob-blocking-executor")
                } as ThreadPoolExecutor
                override fun canExecute(): Boolean = executor.activeCount < executor.corePoolSize
                override val coroutineDispatcher: CoroutineDispatcher = executor.asCoroutineDispatcher()
                override fun shutdown() {
                    executor.shutdown()
                }
            },
            JobExecutionType.NON_BLOCKING to object : DispatcherWrapper {
                private val threadFactory = ForkJoinPool.ForkJoinWorkerThreadFactory { pool ->
                    ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool).also { thread ->
                        thread.name = "kjob-non-blocking-${thread.poolIndex}"
                    }
                }
                private val executor = ForkJoinPool(config.nonBlockingMaxJobs, threadFactory, null, true)
                override fun canExecute(): Boolean = executor.activeThreadCount < executor.parallelism
                override val coroutineDispatcher: CoroutineDispatcher = executor.asCoroutineDispatcher()
                override fun shutdown() {
                    executor.shutdown()
                }
            }
    )
}