@Suppress("DSL_SCOPE_VIOLATION", "UnstableApiUsage")
plugins {
    alias(libs.plugins.jvm)
    alias(libs.plugins.testLogger)
    alias(libs.plugins.dokka)
    alias(libs.plugins.binaryCompat) apply false
    alias(libs.plugins.serialization) apply false
}

allprojects {
    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "kotlin")
    apply(plugin = "com.adarshr.test-logger")

    kotlin {
        target {
            compilations.all {
                kotlinOptions.jvmTarget = "11"
            }
        }
    }
    tasks {
        test {
            useJUnitPlatform()
            systemProperties("user.language" to "en")
            testLogging {
                outputs.upToDateWhen { false }
                showStandardStreams = true
            }
        }
    }

    testlogger {
        theme = com.adarshr.gradle.testlogger.theme.ThemeType.MOCHA_PARALLEL
        showStackTraces = true
        showFullStackTraces = true
        showCauses = true
        slowThreshold = 2000
        showSimpleNames = true
        showStandardStreams = true
    }

    val testJar = tasks.create<Jar>("testJar") {
        archiveClassifier.set("test")
        from(sourceSets.test.get().output)
    }

    configurations {
        create("testArtifacts").extendsFrom(testRuntimeOnly.get())
    }

    artifacts {
        add("testArtifacts", testJar)
    }
}

project(":kjob-example") {
    apply(plugin = "kotlinx-serialization")
    dependencies {
        implementation(project(":kjob-core"))
        implementation(project(":kjob-kron"))
        implementation(project(":kjob-mongo"))
        implementation(project(":kjob-inmem"))
        implementation(project(":kjob-jdbi"))
        implementation(project(":kjob-api"))

        implementation(rootProject.libs.serialization.json)
        implementation(rootProject.libs.ktor.server.core)
        implementation(rootProject.libs.ktor.server.cors)
        implementation(rootProject.libs.ktor.server.netty)
        implementation(rootProject.libs.jdbi.core)
        implementation(rootProject.libs.jdbc.sqlite)
        implementation(rootProject.libs.cronutils) {
            exclude(group = "org.slf4j", module = "slf4j-simple")
        }

        implementation(rootProject.libs.coroutines.core)
        implementation(rootProject.libs.logback)
    }
}

project(":kjob-mongo") {
    apply(from = "../gradle/publishing.gradle.kts")
    apply(plugin = "binary-compatibility-validator")
    dependencies {
        implementation(project(":kjob-core"))
        implementation(rootProject.libs.mongodbReactive)
        implementation(rootProject.libs.coroutines.reactive)

        testImplementation(rootProject.libs.rxjava)
        testImplementation(rootProject.libs.kotest.runner)
        testImplementation(rootProject.libs.kotest.assertions)
        testImplementation(rootProject.libs.embedMongo)
        testImplementation(rootProject.libs.mockk)
        testImplementation(project(path = ":kjob-core", configuration = "testArtifacts"))

        testRuntimeOnly(rootProject.libs.logback)
    }
}

project(":kjob-jdbi") {
    apply(from = "../gradle/publishing.gradle.kts")
    apply(plugin = "binary-compatibility-validator")
    dependencies {
        implementation(project(":kjob-core"))
        implementation(rootProject.libs.serialization.core)
        implementation(rootProject.libs.serialization.json)
        implementation(rootProject.libs.coroutines.core)
        implementation(rootProject.libs.jdbi.core)

        testImplementation(rootProject.libs.jdbc.sqlite)
        testImplementation(rootProject.libs.kotest.runner)
        testImplementation(rootProject.libs.kotest.assertions)
        testImplementation(rootProject.libs.mockk)
        testImplementation(project(path = ":kjob-core", configuration = "testArtifacts"))

        testRuntimeOnly(rootProject.libs.logback)
    }
}

project(":kjob-inmem") {
    apply(from = "../gradle/publishing.gradle.kts")
    apply(plugin = "binary-compatibility-validator")
    dependencies {
        implementation(project(":kjob-core"))
        implementation(rootProject.libs.coroutines.core)

        testImplementation(rootProject.libs.kotest.runner)
        testImplementation(rootProject.libs.kotest.assertions)
        testImplementation(rootProject.libs.mockk)
        testImplementation(project(path = ":kjob-core", configuration = "testArtifacts"))

        testRuntimeOnly(rootProject.libs.logback)
    }
}

project(":kjob-kron") {
    apply(from = "../gradle/publishing.gradle.kts")
    apply(plugin = "binary-compatibility-validator")
    dependencies {
        implementation(project(":kjob-core"))
        implementation(rootProject.libs.coroutines.core)
        implementation(rootProject.libs.cronutils) {
            exclude(group = "org.slf4j", module = "slf4j-simple")
        }
        api(rootProject.libs.slf4j)

        testImplementation(project(":kjob-inmem"))
        testImplementation(rootProject.libs.kotest.runner)
        testImplementation(rootProject.libs.kotest.assertions)
        testImplementation(rootProject.libs.mockk)
        testImplementation(project(path = ":kjob-core", configuration = "testArtifacts"))

        testRuntimeOnly(rootProject.libs.logback)
    }
}

project(":kjob-api") {
    apply(from = "../gradle/publishing.gradle.kts")
    apply(plugin = "binary-compatibility-validator")
    dependencies {
        implementation(project(":kjob-core"))
        implementation(project(":kjob-mongo"))
        implementation(project(":kjob-jdbi"))
        implementation(project(":kjob-inmem"))
        implementation(project(":kjob-kron"))
        implementation(rootProject.libs.ktor.server.core)
        implementation(rootProject.libs.ktor.server.contentNegotiation)
        implementation(rootProject.libs.ktor.server.compression)
        implementation(rootProject.libs.ktor.server.websockets)
        implementation(rootProject.libs.ktor.serialization)
        implementation(rootProject.libs.serialization.core)
        implementation(rootProject.libs.serialization.json)
        implementation(rootProject.libs.coroutines.core)
        implementation(rootProject.libs.cronutils) {
            exclude(group = "org.slf4j", module = "slf4j-simple")
        }

        implementation(rootProject.libs.mongodbReactive)
        implementation(rootProject.libs.jdbi.core)
        testImplementation(rootProject.libs.jdbc.sqlite)
        testImplementation(rootProject.libs.mockk)
        testImplementation(project(path = ":kjob-core", configuration = "testArtifacts"))

        testRuntimeOnly(rootProject.libs.logback)
    }
}

project(":kjob-core") {
    apply(plugin = "kotlinx-serialization")
    apply(from = "../gradle/publishing.gradle.kts")
    apply(plugin = "binary-compatibility-validator")
    dependencies {
        implementation(rootProject.libs.coroutines.core)
        implementation(rootProject.libs.serialization.core)
        implementation(rootProject.libs.serialization.json)
        api(rootProject.libs.slf4j)

        testImplementation(rootProject.libs.kotest.runner)
        testImplementation(rootProject.libs.kotest.assertions)
        testImplementation(rootProject.libs.mockk)
        testRuntimeOnly(rootProject.libs.logback)
    }
}