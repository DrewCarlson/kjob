package io.kotest.provided

import com.mongodb.reactivestreams.client.MongoClient
import com.mongodb.reactivestreams.client.MongoClients
import de.flapdoodle.embed.mongo.config.Net
import de.flapdoodle.embed.mongo.distribution.Version
import de.flapdoodle.embed.mongo.transitions.Mongod
import de.flapdoodle.embed.mongo.transitions.RunningMongodProcess
import de.flapdoodle.embed.process.runtime.Network
import de.flapdoodle.reverse.transitions.Start
import io.kotest.core.config.AbstractProjectConfig
import java.net.InetAddress

// Code is executed before and after test engine is started.
// see https://github.com/kotest/kotest/blob/master/doc/reference.md#project-config

object ProjectConfig : AbstractProjectConfig() {

    private const val host = "localhost"
    private val port = Network.freeServerPort(InetAddress.getLocalHost())
    private val mongod: Triple<RunningMongodProcess, String, Int> by lazy {
        val process = Mongod.instance()
            .withNet(
                Start.to(Net::class.java)
                    .initializedWith(Net.of(host, port, Network.localhostIsIPv6()))
            )
            .start(Version.Main.V4_4)
            .current()
        Triple(process, host, port)
    }

    fun newMongoClient(): MongoClient {
        val (_, host, port) = mongod
        return MongoClients.create("mongodb://$host:$port/?uuidRepresentation=STANDARD")
    }

    override suspend fun afterProject() {
        super.afterProject()
        val (exe) = mongod
        exe.stop()
    }
}
