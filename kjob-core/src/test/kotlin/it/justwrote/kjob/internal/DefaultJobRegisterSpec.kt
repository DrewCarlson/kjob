package it.justwrote.kjob.internal

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import it.justwrote.kjob.Job
import it.justwrote.kjob.KJob
import it.justwrote.kjob.dsl.JobContext
import it.justwrote.kjob.job.JobExecutionType
import it.justwrote.kjob.job.JobProps
import it.justwrote.kjob.job.ScheduledJob
import it.justwrote.kjob.repository.JobRepository
import it.justwrote.kjob.utils.waitSomeTime
import kotlinx.coroutines.Dispatchers
import java.util.*
import java.util.concurrent.CountDownLatch

class DefaultJobRegisterSpec : ShouldSpec() {

    init {
        val config = KJob.Configuration()

        should("return empty set if there is no job defined for given execution type") {
            val testee = DefaultJobRegister(config)
            testee.jobs(JobExecutionType.BLOCKING).shouldBeEmpty()
            testee.jobs(JobExecutionType.NON_BLOCKING).shouldBeEmpty()
        }

        should("throw an exception if no runnable job has been found for given name") {
            val testee = DefaultJobRegister(config)
            shouldThrow<NoSuchElementException> {
                testee.get("unknown-job")
            }
        }

        should("register a runnable job with a provided execution block") {
            val testee = DefaultJobRegister(KJob.Configuration().apply {
                defaultJobExecutor = JobExecutionType.NON_BLOCKING
                maxRetries = 12345
            })
            val testJob = object : Job("test-job") {}
            val latch = CountDownLatch(1)
            testee.register(testJob) {
                execute {
                    latch.countDown()
                }
            }
            val runnableJob = testee.get("test-job")
            runnableJob.executionType shouldBe JobExecutionType.NON_BLOCKING
            runnableJob.maxRetries shouldBe 12345
            val sjMock = mockk<ScheduledJob>()
            val jobRepositoryMock = mockk<JobRepository>()
            every { sjMock.id } returns "my-internal-id"
            every { sjMock.settings.id } returns "my-job-id"
            every { sjMock.settings.name } returns "test-job"
            every { sjMock.progress.step } returns 0
            coEvery { jobRepositoryMock.startProgress("my-internal-id") } returns true
            coEvery { jobRepositoryMock.get("my-internal-id") } returns sjMock
            coEvery { jobRepositoryMock.setProgressMax("my-internal-id", 0) } returns true
            coEvery { jobRepositoryMock.completeProgress("my-internal-id") } returns true
            val result = runnableJob.execute(JobContext<Job>(
                    Dispatchers.Unconfined,
                    JobProps(emptyMap()),
                    sjMock,
                    jobRepositoryMock
            ))
            result shouldBe JobSuccessful

            latch.waitSomeTime()
        }
    }
}