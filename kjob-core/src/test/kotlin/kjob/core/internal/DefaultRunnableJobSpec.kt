package kjob.core.internal

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.throwable.shouldHaveMessage
import io.kotest.matchers.types.shouldBeTypeOf
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kjob.core.Job
import kjob.core.KJob
import kjob.core.dsl.JobContext
import kjob.core.dsl.JobContextWithProps
import kjob.core.dsl.JobRegisterContext
import kjob.core.dsl.KJobFunctions
import kjob.core.job.JobExecutionType
import kjob.core.job.JobProps
import kjob.core.job.ScheduledJob
import kjob.core.repository.JobRepository
import kjob.core.utils.waitSomeTime
import kotlinx.coroutines.Dispatchers
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.CountDownLatch

class DefaultRunnableJobSpec : ShouldSpec() {

    object TestJob : Job("test-job") {
        val prop1 = integer("int-test")
        val prop2 = string("string-test").nullable()
    }

    @Suppress("UNCHECKED_CAST")
    private fun <J : Job> defaultRunnableJob(job: J, config: KJob.Configuration, block: JobRegisterContext<J, JobContextWithProps<J>>.(J) -> KJobFunctions<J, JobContextWithProps<J>>): DefaultRunnableJob<J> =
            DefaultRunnableJob(job, config, block as JobRegisterContext<J, JobContext<J>>.(J) -> KJobFunctions<J, JobContext<J>>)

    init {
        val sjMock = mockk<ScheduledJob>()
        val jobRepositoryMock = mockk<JobRepository>()

        beforeTest {
            clearMocks(sjMock, jobRepositoryMock)

            every { sjMock.id } returns "my-internal-id"
            every { sjMock.settings.id } returns "my-job-id"
            every { sjMock.settings.name } returns "test-job"
            every { sjMock.progress.step } returns 0

            coEvery { jobRepositoryMock.startProgress("my-internal-id") } returns true
            coEvery { jobRepositoryMock.get("my-internal-id") } returns sjMock
            coEvery { jobRepositoryMock.setProgressMax("my-internal-id", 0) } returns true
            coEvery { jobRepositoryMock.completeProgress("my-internal-id") } returns true
        }

        val config = KJob.Configuration()

        should("successfully execute defined job") {
            val conf = KJob.Configuration().apply {
                defaultJobExecutor = JobExecutionType.NON_BLOCKING
                maxRetries = 12345
            }
            val latch = CountDownLatch(1)
            val testee = defaultRunnableJob(TestJob, conf) {
                execute {
                    val prop1 = props[TestJob.prop1]
                    val prop2 = props[TestJob.prop2]
                    prop1 shouldBe 3
                    prop2.shouldBeNull()
                    latch.countDown()
                }
            }
            testee.executionType shouldBe JobExecutionType.NON_BLOCKING
            testee.maxRetries shouldBe 12345
            val result = testee.execute(
                JobContextWithProps<TestJob>(
                    Dispatchers.Unconfined,
                    JobProps(mapOf("int-test" to 3)),
                    sjMock,
                    jobRepositoryMock
            )
            )
            result shouldBe JobSuccessful

            latch.waitSomeTime()
        }

        should("fail to execute job if required props are missing") {
            val testee = defaultRunnableJob(TestJob, config) {
                execute {
                    println(props[TestJob.prop1])
                }
            }
            val result = testee.execute(
                JobContextWithProps<TestJob>(
                    Dispatchers.Unconfined,
                    JobProps(emptyMap()),
                    sjMock,
                    jobRepositoryMock
            )
            )
            result.shouldBeTypeOf<JobError>()
            result.throwable.shouldBeTypeOf<NullPointerException>()
        }

        should("fail to execute job if job repository is not successful") {
            coEvery { jobRepositoryMock.startProgress("my-internal-id") } returns false
            val testee = DefaultRunnableJob(TestJob, config) {
                execute {
                    // nothing
                }
            }

            val result = testee.execute(
                JobContextWithProps<TestJob>(
                    Dispatchers.Unconfined,
                    JobProps(emptyMap()),
                    sjMock,
                    jobRepositoryMock
            )
            )

            result.shouldBeTypeOf<JobError>()
            result.throwable.shouldHaveMessage("Failed to start execution.")

        }

        should("fail to complete job if call to job repository is not successful") {
            coEvery { jobRepositoryMock.completeProgress("my-internal-id") } returns false
            val testee = DefaultRunnableJob(TestJob, config) {
                execute {
                    // nothing
                }
            }

            val result = testee.execute(
                JobContextWithProps<TestJob>(
                    Dispatchers.Unconfined,
                    JobProps(emptyMap()),
                    sjMock,
                    jobRepositoryMock
            )
            )

            result.shouldBeTypeOf<JobError>()
            result.throwable.shouldHaveMessage("Failed to complete execution.")

        }

        should("fail to complete job if call to job repository is not successful #2") {
            coEvery { jobRepositoryMock.setProgressMax("my-internal-id", 0) } returns false
            val testee = DefaultRunnableJob(TestJob, config) {
                execute {
                    // nothing
                }
            }

            val result = testee.execute(
                JobContextWithProps<TestJob>(
                    Dispatchers.Unconfined,
                    JobProps(emptyMap()),
                    sjMock,
                    jobRepositoryMock
            )
            )

            result.shouldBeTypeOf<JobError>()
            result.throwable.shouldHaveMessage("Failed to complete execution.")

        }

        should("execute 'onError' block if execution fails") {
            val latch = CountDownLatch(1)
            val testee = DefaultRunnableJob(TestJob, config) {
                execute {
                    error("#test")
                }.onComplete {
                    error("#test2")
                }.onError {
                    jobName shouldBe "test-job"
                    jobId shouldBe "my-job-id"
                    error shouldHaveMessage "#test"
                    latch.countDown()
                }
            }

            val result = testee.execute(
                JobContextWithProps<TestJob>(
                    Dispatchers.Unconfined,
                    JobProps(emptyMap()),
                    sjMock,
                    jobRepositoryMock
            )
            )

            result.shouldBeTypeOf<JobError>()
            result.throwable.shouldHaveMessage("#test")


            latch.countDown()
        }

        should("execute 'onComplete' block if execution successful") {
            val now = Instant.now()
            every { sjMock.progress.startedAt } returns now
            every { sjMock.progress.completedAt } returns now.plusMillis(100)

            val latch = CountDownLatch(1)
            val testee = DefaultRunnableJob(TestJob, config) {
                execute {
                    // nothing
                }.onComplete {
                    jobName shouldBe "test-job"
                    jobId shouldBe "my-job-id"
                    time() shouldBe Duration.of(100, ChronoUnit.MILLIS)
                    latch.countDown()
                }
            }

            val result = testee.execute(
                JobContextWithProps<TestJob>(
                    Dispatchers.Unconfined,
                    JobProps(emptyMap()),
                    sjMock,
                    jobRepositoryMock
            )
            )

            result shouldBe JobSuccessful

            latch.waitSomeTime()
        }

        should("handle error in 'onComplete' block") {
            val testee = DefaultRunnableJob(TestJob, config) {
                execute {
                    // nothing
                }.onComplete {
                    error("#test")
                }
            }

            val result = testee.execute(
                JobContextWithProps<TestJob>(
                    Dispatchers.Unconfined,
                    JobProps(emptyMap()),
                    sjMock,
                    jobRepositoryMock
            )
            )

            result.shouldBeTypeOf<JobError>()
            result.throwable.shouldHaveMessage("#test")

        }

        should("handle error in 'onError' block") {
            val testee = DefaultRunnableJob(TestJob, config) {
                execute {
                    error("#test1")
                }.onError {
                    error("#test2")
                }
            }

            val result = testee.execute(
                JobContextWithProps<TestJob>(
                    Dispatchers.Unconfined,
                    JobProps(emptyMap()),
                    sjMock,
                    jobRepositoryMock
            )
            )

            result.shouldBeTypeOf<JobError>()
            result.throwable.shouldHaveMessage("#test1")

        }
    }
}