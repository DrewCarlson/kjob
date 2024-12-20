@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    alias(libs.plugins.jvm)
    alias(libs.plugins.testLogger)
    alias(libs.plugins.dokka)
    alias(libs.plugins.spotless)
    alias(libs.plugins.kover)
    alias(libs.plugins.binaryCompat) apply false
    alias(libs.plugins.serialization) apply false
    alias(libs.plugins.mavenPublish) apply false
}

allprojects {
    repositories {
        mavenCentral()
    }
}

version = System.getenv("GITHUB_REF")?.substringAfter("refs/tags/v", version.toString()) ?: version

subprojects {
    apply(plugin = "kotlin")
    apply(plugin = "com.adarshr.test-logger")
    apply(plugin = "com.diffplug.spotless")
    apply(plugin = "org.jetbrains.kotlinx.kover")

    kotlin {
        target {
            compilations.all {
                check(JavaVersion.current().isJava11Compatible) { "Kjob requires JDK 11+" }
                kotlinOptions.jvmTarget = JavaVersion.current().majorVersion
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

    kover {}
    configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        kotlin {
            //target("**/**.kt")
            //ktlint(libs.versions.ktlint.get())
            //    .editorConfigOverride(mapOf("disabled_rules" to "no-wildcard-imports,no-unused-imports"))
        }
    }
}

dependencies {
    kover(project(":kjob-api"))
    kover(project(":kjob-core"))
    kover(project(":kjob-inmem"))
    kover(project(":kjob-jdbi"))
    kover(project(":kjob-kron"))
    kover(project(":kjob-mongo"))
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
    apply(plugin = "binary-compatibility-validator")
    apply(plugin = "org.jetbrains.dokka")
    apply(plugin = "com.vanniktech.maven.publish")
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
    apply(plugin = "binary-compatibility-validator")
    apply(plugin = "org.jetbrains.dokka")
    apply(plugin = "com.vanniktech.maven.publish")
    dependencies {
        implementation(project(":kjob-core"))
        implementation(rootProject.libs.serialization.core)
        implementation(rootProject.libs.serialization.json)
        implementation(rootProject.libs.coroutines.core)
        implementation(rootProject.libs.jdbi.core)

        testImplementation(rootProject.libs.jdbc.sqlite)
        testImplementation(rootProject.libs.jdbc.postgres)
        testImplementation(rootProject.libs.jdbc.mysql)
        testImplementation(rootProject.libs.kotest.runner)
        testImplementation(rootProject.libs.kotest.assertions)
        testImplementation(rootProject.libs.mockk)
        testImplementation(project(path = ":kjob-core", configuration = "testArtifacts"))

        testRuntimeOnly(rootProject.libs.logback)
    }
}

project(":kjob-inmem") {
    apply(plugin = "binary-compatibility-validator")
    apply(plugin = "org.jetbrains.dokka")
    apply(plugin = "com.vanniktech.maven.publish")
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
    apply(plugin = "binary-compatibility-validator")
    apply(plugin = "org.jetbrains.dokka")
    apply(plugin = "com.vanniktech.maven.publish")
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
    apply(plugin = "binary-compatibility-validator")
    apply(plugin = "org.jetbrains.dokka")
    apply(plugin = "com.vanniktech.maven.publish")
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
    apply(plugin = "binary-compatibility-validator")
    apply(plugin = "org.jetbrains.dokka")
    apply(plugin = "com.vanniktech.maven.publish")
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