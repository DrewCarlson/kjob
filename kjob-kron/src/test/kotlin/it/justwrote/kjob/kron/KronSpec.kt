package kjob.kron

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kjob.inmem.InMem
import kjob.core.KronJob
import kjob.core.kjob
import kjob.core.utils.waitSomeTime
import java.util.concurrent.CountDownLatch

class KronSpec : ShouldSpec() {

    object EverySecond : KronJob("every-second", "* * * ? * * *")

    init {
        should("create a fully working kjob instance with Kron extension") {
            val kjob = kjob(InMem) {
                extension(KronModule)
            }.start()

            val latch = CountDownLatch(1)
            kjob(Kron).kron(EverySecond) {
                execute {
                    latch.countDown()
                }
            }

            latch.waitSomeTime(3500) shouldBe true
            kjob.shutdown()
        }
    }
}