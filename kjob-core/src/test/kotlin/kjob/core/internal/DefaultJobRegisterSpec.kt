package kjob.core.internal

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kjob.core.Job
import kjob.core.KJob
import kjob.core.dsl.JobContextWithProps
import kjob.core.job.JobExecutionType
import kjob.core.job.JobProps
import kjob.core.job.ScheduledJob
import kjob.core.repository.JobRepository
import kjob.core.utils.waitSomeTime
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import java.util.*
import java.util.concurrent.CountDownLatch

class DefaultJobRegisterSpec : ShouldSpec() {

    init {

        should("return empty set if there is no job defined for given execution type") {
            val testee = DefaultJobRegister()
            testee.jobs(JobExecutionType.BLOCKING).shouldBeEmpty()
            testee.jobs(JobExecutionType.NON_BLOCKING).shouldBeEmpty()
        }

        should("throw an exception if no runnable job has been found for given name") {
            val testee = DefaultJobRegister()
            shouldThrow<NoSuchElementException> {
                testee.get("unknown-job")
            }
        }

        should("register a runnable job with a provided execution block") {
            val testee = DefaultJobRegister()
            val testJob = object : Job("test-job") {}
            val latch = CountDownLatch(1)
            val runnableJob = DefaultRunnableJob(testJob, KJob.Configuration()) {
                execute {
                    latch.countDown()
                }
            }
            testee.register(runnableJob)

            val actual = testee.get("test-job")
            actual shouldBe runnableJob
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
            val result = runnableJob.execute(
                JobContextWithProps<Job>(
                    Dispatchers.Unconfined,
                    JobProps(emptyMap(), Json),
                    sjMock,
                    jobRepositoryMock
            )
            )
            result shouldBe JobSuccessful

            latch.waitSomeTime()
        }
    }
}