package example.kron

import kjob.core.KronJob
import kjob.core.kjob
import kjob.inmem.InMem
import kjob.kron.Kron
import kjob.kron.KronModule
import kotlinx.coroutines.delay
import java.time.Instant

object PrintStuff : KronJob("print-stuff", "* * * ? * * *")
object PrintMoreStuff : KronJob("print-more-stuff", "*/10 * * ? * * *")

suspend fun main() {
    val kjob = kjob(InMem) {
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
        }
    }

    delay(25000) // This is just to prevent a premature shutdown
    kjob.shutdown()
}
