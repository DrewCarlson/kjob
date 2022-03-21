# kjob

[![Maven Central](https://img.shields.io/maven-central/v/org.drewcarlson/kjob-core?label=maven&color=blue)](https://search.maven.org/search?q=g:org.drewcarlson%20a:kjob-*)
[![GitHub Build Status](https://img.shields.io/github/workflow/status/DrewCarlson/kjob/Tests/main?style=flat)](https://github.com/DrewCarlson/kjob/actions?query=workflow%3ATests)

A coroutine based Job scheduler for Kotlin. Requires Java 11+.

Forked from [justwrote/kjob](https://github.com/justwrote/kjob).

## Features

* Job creation and scheduling DSL
* Persist scheduled jobs (SQL/JDBC, mongoDB)
* [Cron](#cron) job scheduling syntax
* Supports multiple instances
* Failed job rescheduling
* Configurable pools for blocking and non-blocking jobs
* [Ktor](https://ktor.io) powered [Json REST APi](#Json-REST-API)
* Custom [extensions](#extensions) API

## Installation

[![Maven Central](https://img.shields.io/maven-central/v/org.drewcarlson/kjob-core?label=maven&color=blue)](https://search.maven.org/search?q=g:org.drewcarlson%20a:kjob-*)
![Sonatype Nexus (Snapshots)](https://img.shields.io/nexus/s/org.drewcarlson/kjob-core?server=https%3A%2F%2Fs01.oss.sonatype.org)

```kotlin
repositories {
  mavenCentral()
  // (Optional) For Snapshots:
  maven(url = "https://s01.oss.sonatype.org/content/repositories/snapshots/")
}

dependencies {
  implementation("org.drewcarlson:kjob-core:<version>")
  implementation("org.drewcarlson:kjob-api:<version>") // for Ktor Json API routes
  implementation("org.drewcarlson:kjob-jdbi:<version>") // for SQL/JDBC persistence
  implementation("org.drewcarlson:kjob-mongo:<version>") // for mongoDB persistence
  testImplementation("org.drewcarlson:kjob-inmem:<version>") // for in-memory 'persistence' (e.g. tests)
}
```

## Using kjob

```kotlin
import kjob.mongo.Mongo
import kjob.core.Job
import kjob.core.job.JobExecutionType
import kjob.core.KJob

object OrderCreatedEmail : Job("order-created-email") {
    val recipient = string("recipient")
}

// start kjob with mongoDB persistence and default configuration
val kjob = kjob(Mongo).start()

kjob.register(OrderCreatedEmail) {
    executionType = JobExecutionType.NON_BLOCKING // our fake email client is non blocking
    maxRetries = 3
    execute {
        val to = props[it.recipient] // getting address from customer
        client.sendTo(to, subject, body)
    }.onError {
        // errors will automatically logged but we might want to do some metrics or something 
    }
}

kjob.schedule(OrderCreatedEmail) {
    props[it.recipient] = "customer@example.com"
}

// or provide some delay for the scheduling
kjob.schedule(OrderCreatedEmail, 5.seconds) {
    props[it.recipient] = "customer@example.com"
}
// this runs the job not immediately but - you may guess it already - in 5 seconds!
```

For more details please take a look at the [examples](https://github.com/DrewCarlson/kjob/blob/main/kjob-example/src/main/kotlin).

## Starting kjob

Multiple schedulers are running in the background after starting kjob.
There is one looking for new jobs every second (period can be defined in the configuration).
If a job has been found that has not yet been started (or reset after an error) and the kjob instance is currently
not executing too many other jobs of the same kind (there are blocking and non-blocking jobs) kjob will process it.
The second scheduler is handling the locking. It indirectly tells the other kjob instances that this one is still alive.
The last scheduler is cleaning up locked jobs of other not responding kjob instances to make the jobs available again for execution.

## Multiple kjob instances

To be fault-tolerant you sometimes want to have multiple instances of your job processor.
This might be in the same app or on different nodes.
Therefore, every kjob instance has a unique id which will be added to jobs it is currently executing.
This locks a job to a specific kjob instance.
If a kjob instance goes offline while executing a job, another kjob instance will remove the lock after a configurable time and retry.

## Changing Configuration

Changing the config is fairly easy.
There is not another config file and everything will be done in code - so you can use your own configuration.

```kotlin
kjob(InMem) {
    nonBlockingMaxJobs = 10 // how many non-blocking jobs will be executed at max in parallel per instance
    blockingMaxJobs = 3 // same for blocking jobs
    maxRetries = 5 // how often will a job be retried until it fails
    defaultJobExecutor = JobExecutionType.BLOCKING // default job execution type
    isWorker = true // disable to prevent this instance from executing jobs

    exceptionHandler = { t: Throwable -> logger.error("Unhandled exception", t) } // default error handler for coroutines
    keepAliveExecutionPeriodInSeconds = 60 // the time between 'I am alive' notifications
    jobExecutionPeriodInSeconds = 1 // the time between new job executions
    cleanupPeriodInSeconds = 300 // the time between job clean ups
    cleanupSize = 50 // the amount of jobs that will be cleaned up per schedule
}.start()
```

## Json REST API

The `kjob-api` module provides [Ktor](https://ktor.io) server configuration and routes for managing jobs and instances.

```kotlin
val kjob = kjob(JdbiKJob) {
    connectionString = "..."
    extension(KJobApiModule)
}

embeddedServer(Netty) {
    installKJobApi(
        kjobInstance = kjob, // Or listOf(kjob, ...)
        rootRoute = null, // The root `route { .. }` to install kjob routes under
        installSerialization = true // Automatically install Json content negotiation
    )
}.start(wait = true)
```

```
Routes:
   /kjob/statuses       - GET available job statuses    -
   /kjob/stats          - GET job counts by status      - Parameters: ?instanceId=<kjob worker ID>&names=job1,job2
   /kjob/job-types      - GET all registered job types  -
   /kjob/jobs           - GET all persisted jobs        - Parameters: ?limit=10&status=COMPLETE,ERROR&names=job1,job2
   /kjob/jobs/<id>      - GET persisted job by id       - Parameters: ?instanceId=<kjob worker ID>
   /kjob/instances      - GET all kjob instances        -
   /kjob/instances/<id> - GET kjob instance by id       -
```

A complete Ktor server example can be found in [Example6.kt](kjob-example/src/main/kotlin/Example6.kt).

## JDBI (SQL) Configuration

Using [JDBI](https://jdbi.org/), KJob can persist jobs in SQL databases.
You only need to include the JDBC driver dependency and provide a connection string.

Drivers: [SQLite](https://github.com/xerial/sqlite-jdbc), [MySQL](https://github.com/mysql/mysql-connector-j), [PostegreSQL](https://github.com/pgjdbc/pgjdbc)

```kotlin
import kjob.core.kjob
kjob(JdbiKJob) {
    connectionString = "jdbc:sqlite::memory:" // JDBC connection string
    jdbi = null // Optional: Jdbi instance used in place of `connectionString`
    handle = null // Optional: Handle used in place of `jdbi` and `connectionString`
    jobTableName = "kjobJobs" // Optional: Table name for job data
    lockTableName = "kjobLocks" // Optional: Table name for lock states
    expireLockInMinutes = 5L // Optional: Expire locks after this duration
}
```

## MongoDB Configuration

KJob can persist jobs in MongoDb databases, requiring only a connection string.

<details>
<summary>MongoDB Configuration (Click to expand)</summary>

```kotlin
kjob(Mongo) {
    // all the config above plus those:
    connectionString = "mongodb://localhost" // the mongoDB specific connection string 
    client = null // if a client is specified the 'connectionString' will be ignored
    databaseName = "kjob" // the database where the collections below will be created
    jobCollection = "kjob-jobs" // the collection for all jobs
    lockCollection = "kjob-locks" // the collection for the locking
    expireLockInMinutes = 5L // using the TTL feature of mongoDB to expire a lock
}.start()
```

</details>

## Extensions

If you want to add new features to kjob you can do so with an extension.

<details>
<summary>Extension example (Click to expand)</summary>

```kotlin
object ShowIdExtension : ExtensionId<ShowIdEx>

class ShowIdEx(
    private val config: Configuration,
    private val kjobConfig: BaseKJob.Configuration,
    private val kjob: BaseKJob<BaseKJob.Configuration>
) : BaseExtension(ShowIdExtension) {
    class Configuration : BaseExtension.Configuration()

    fun showId() {
        // here you have access to some internal properties
        println("KJob has the following id: ${kjob.id}")
    }
}

object ShowIdModule : ExtensionModule<ShowIdEx, ShowIdEx.Configuration, BaseKJob<BaseKJob.Configuration>, BaseKJob.Configuration> {
    override val id: ExtensionId<ShowIdEx> = ShowIdExtension
    override fun create(
        configure: ShowIdEx.Configuration.() -> Unit,
        kjobConfig: BaseKJob.Configuration
    ): (BaseKJob<BaseKJob.Configuration>) -> ShowIdEx {
        return { ShowIdEx(ShowIdEx.Configuration().apply(configure), kjobConfig, it) }
    }
}

val kjob = kjob(InMem) {
    extension(ShowIdModule) // register our extension and bind it to the kjob lifecycle
}

kjob(ShowIdExtension).showId() // access our new extension method
```

</details>

For a more advanced version see [Example_Extension.kt](https://github.com/DrewCarlson/kjob/blob/main/kjob-example/src/main/kotlin/Example_Extension.kt).

## Cron

With kjob you are also able to schedule jobs with the familiar cron expression.
To get Kron - the name of the extension to enable Cron scheduling in kjob - you need to add the following dependency:

```kotlin
dependencies {
  implementation("org.drewcarlson:kjob-kron:<version>")
}
``` 

After that you can schedule cron jobs as easy as every other job with kjob.

```kotlin
// define a Kron job with a name and a cron expression (e.g. 5 seconds)
object PrintStuff : KronJob("print-stuff", "*/5 * * ? * * *")

val kjob = kjob(InMem) {
    extension(KronModule) // enable the Kron extension
}

// define the executed code
kjob(Kron).kron(PrintStuff) {
    maxRetries = 3 // and you can access the already familiar settings you are used to
    execute {
        println("${Instant.now()}: executing kron task '${it.name}' with jobId '$jobId'")
    }
}
```

You can find more in this [example](https://github.com/DrewCarlson/kjob/blob/main/kjob-example/src/main/kotlin/Example_Kron.kt)


## Roadmap

Here is an unordered list of features that I would like to see in kjob.
If you consider one of them important please open an issue.

- Dashboard
- Redis job storage
- Priority support
- Backoff algorithm for failed jobs

## License

kjob is licensed under the [Apache 2.0 License](https://github.com/DrewCarlson/kjob/blob/main/LICENSE).