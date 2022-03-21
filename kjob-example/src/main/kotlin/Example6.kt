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
import org.jdbi.v3.core.Jdbi
import java.time.Duration
import java.time.Instant
import java.util.*
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds


object LogNumber : Job("log-number") {
    val number = integer("number")
}

object LogText : Job("log-text") {
    val text = string("text")
}

object PrintStuff : KronJob("print-stuff", "* * */10 ? * * *")
object PrintMoreStuff : KronJob("print-more-stuff", "*/10 * * ? * * *")

suspend fun main() {
    val dbHandle = Jdbi.create("jdbc:sqlite::memory:").open()

    val kjob = kjob(JdbiKJob) {
        handle = dbHandle
        extension(KJobApiModule)
        extension(KronModule)
    }.start()

    kjob(Kron).kron(PrintStuff) {
        maxRetries = 3
        execute {
            println("${Instant.now()}: executing kron task '${it.name}' with jobId '$jobId'")
        }
    }

    kjob(Kron).kron(PrintMoreStuff) {
        execute {
            println("${Instant.now()}: executing kron task '${it.name}' with jobId '$jobId'")
            delay(2.seconds)
        }
    }

    kjob.register(LogNumber) {
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
    kjob.register(LogText) {
        execute {
            setInitialMax(5L)
            println(props[it.text])
            if (System.currentTimeMillis() % 2L == 0L) {
                error("Failed to finish job")
            }
            repeat(5) {
                delay(1.seconds)
                step()
            }
        }
    }

    repeat(25) { i ->
        kjob.schedule(LogNumber, Duration.ofMinutes(i.toLong())) {
            props[it.number] = Random.nextInt()
        }
    }
    repeat(5) { i ->
        val delay = Duration.ofDays(i * 100L)
        kjob.schedule(LogText, delay) {
            jobId = "${delay.toDays()}-days"
            props[it.text] = "$delay"
        }
    }

    embeddedServer(Netty, port = 9999) {
        installKJobApi(kjob)
    }.start(wait = true)
}