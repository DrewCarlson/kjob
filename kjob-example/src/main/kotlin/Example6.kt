package example6

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kjob.api.KJobApiModule
import kjob.api.installKJobApi
import kjob.core.Job
import kjob.core.KronJob
import kjob.core.kjob
import kjob.jdbi.JdbiKJob
import kjob.kron.Kron
import kjob.kron.KronModule
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import org.jdbi.v3.core.Jdbi
import java.time.Duration
import java.time.Instant
import java.util.*
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds


@Serializable
data class TestData(
    val number: Int,
    val text: String,
)

object LogNumber : Job("log-number") {
    val testData = serializable("test", TestData::class)
    val number = integer("number")
}

object LogText : Job("log-text") {
    val text = string("text")
}

object PrintStuff : KronJob("print-stuff", "* * * ? * * *")
object PrintMoreStuff : KronJob("print-more-stuff", "*/10 * * ? * * *")

suspend fun main() {
    val dbHandle = Jdbi.create("jdbc:sqlite::memory:").open()

    val schedulerKjob = kjob(JdbiKJob) {
        handle = dbHandle
        isWorker = false
        extension(KJobApiModule)
    }.start()
    val workerKjob = kjob(JdbiKJob) {
        handle = dbHandle
        extension(KJobApiModule)
        extension(KronModule)
    }.start()

    workerKjob(Kron).kron(PrintStuff) {
        maxRetries = 3
        execute {
            println("${Instant.now()}: executing kron task '${it.name}' with jobId '$jobId'")
        }
    }

    workerKjob(Kron).kron(PrintMoreStuff) {
        execute {
            println("${Instant.now()}: executing kron task '${it.name}' with jobId '$jobId'")
        }
    }

    workerKjob.register(LogNumber) {
        maxRetries = 0
        execute {
            setInitialMax(5L)
            val number = props[it.number]
            if (number % 2 == 0) {
                error("Failed to finish job")
            }
            repeat(5) {
                delay(10)
                step()
            }
        }
    }
    workerKjob.register(LogText) {
        execute {
            setInitialMax(5L)
            println(props[it.text])
            if (System.currentTimeMillis() % 2L == 0L) {
                error("Failed to finish job")
            }
            repeat(5) {
                delay(30.seconds)
                step()
            }
        }
    }

    repeat(1_000) { i ->
        schedulerKjob.schedule(LogNumber, Duration.ofSeconds(i.toLong())) {
            props[it.number] = Random.nextInt()
        }
        schedulerKjob.schedule(LogText, Duration.ofSeconds(i.toLong())) {
            props[it.text] = Base64.getEncoder().encodeToString(Random.nextBytes(8))
        }
    }
    repeat(10) {
        schedulerKjob.schedule(LogText, Duration.ofDays(500)) {
            props[it.text] = Base64.getEncoder().encodeToString(Random.nextBytes(8))
        }
    }

    embeddedServer(Netty, port = 9999) {
        installKJobApi(listOf(schedulerKjob, workerKjob))
    }.start(wait = true)
}