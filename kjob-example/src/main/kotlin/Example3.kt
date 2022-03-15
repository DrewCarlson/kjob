package example3

import kjob.core.Job
import kjob.core.kjob
import kjob.mongo.Mongo
import kjob.core.job.JobExecutionType
import kjob.core.KJob
import kotlinx.coroutines.delay

interface EmailClient {
    suspend fun sendTo(to: String, subject: String, body: String)
}

class PrintlnEmailClient : EmailClient {
    override suspend fun sendTo(to: String, subject: String, body: String) {
        println("sending email to '$to' with subject: $subject")
    }
}

object OrderCreatedEmail : Job("order-created-email") {
    val recipient = string("recipient")
    val orderId = string("orderId")
}

class EmailToCustomer(private val kjob: KJob, private val client: EmailClient) {

    init {
        kjob.register(OrderCreatedEmail) {
            executionType = JobExecutionType.NON_BLOCKING // our email client is non blocking
            maxRetries = 3
            execute {
                val orderId = props[it.orderId]
                val subject = "Order confirmation $orderId"
                val body = "..."
                val to = props[it.recipient] // getting address from customer
                client.sendTo(to, subject, body)
            }.onError {
                // errors will automatically logged but we might want to do some metrics or something
            }
        }
    }

    suspend fun scheduleEmailToCustomer(recipient: String, orderId: String) {
        kjob.schedule(OrderCreatedEmail) {
            jobId = orderId // prevent the same 'job' scheduled twice.
            props[it.orderId] = orderId
            props[it.recipient] = recipient
        }
    }
}

suspend fun main() {
    // start kjob with mongoDB persistence and default configuration
    val kjob = kjob(Mongo).start()

    try {
        val client: EmailClient = PrintlnEmailClient()

        val emailToCustomer = EmailToCustomer(kjob, client)
        emailToCustomer.scheduleEmailToCustomer("customer@example.com", "4711")

        delay(1100) // This is just to prevent a premature shutdown
    } finally {
        kjob.shutdown()
    }
}
