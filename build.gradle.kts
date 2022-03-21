import org.jetbrains.compose.compose
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig
import org.jetbrains.kotlin.gradle.targets.js.yarn.yarn

@Suppress("DSL_SCOPE_VIOLATION", "UnstableApiUsage")
plugins {
    alias(libs.plugins.jvm)
    alias(libs.plugins.testLogger)
    alias(libs.plugins.dokka)
    alias(libs.plugins.serialization) apply false
    alias(libs.plugins.composejb) apply false
}

allprojects {
    yarn.lockFileDirectory = rootDir.resolve("gradle/kotlin-js-store")
    repositories {
        mavenCentral()
    }
}

subprojects {
    if (name == "kjob-dashboard") return@subprojects
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
    dependencies {
        implementation(project(":kjob-core"))
        implementation(project(":kjob-kron"))
        implementation(project(":kjob-mongo"))
        implementation(project(":kjob-inmem"))
        implementation(project(":kjob-jdbi"))
        implementation(project(":kjob-api"))

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

project(":kjob-dashboard") {
    apply(plugin = "kotlin-multiplatform")
    apply(plugin = "kotlinx-serialization")
    apply(plugin = "org.jetbrains.compose")
    extensions.getByType<org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension>().apply {
        js(IR) {
            browser {
                commonWebpackConfig {
                    cssSupport.enabled = true
                }
                webpackTask {
                    outputFileName = "kjob-dashboard.bundle.js"
                }
                runTask {
                    outputFileName = "kjob-dashboard.bundle.js"
                    devServer = KotlinWebpackConfig.DevServer(
                        open = false,
                        proxy = mutableMapOf(
                            "/kjob/*" to mapOf<String, Any>(
                                "target" to "http://localhost:9999",
                                "ws" to false,
                            )
                        ),
                        static = mutableListOf("$buildDir/processedResources/js/main")
                    )
                }
            }
            binaries.executable()
        }
        this.sourceSets {
            val jsMain by getting {
                dependencies {
                    implementation(rootProject.libs.ktor.client.core)
                    implementation(rootProject.libs.ktor.client.js)
                    implementation(rootProject.libs.ktor.client.contentNegotiation)
                    implementation(rootProject.libs.ktor.client.websockets)
                    implementation(rootProject.libs.ktor.serialization)
                    implementation(rootProject.libs.kotlinjs.extensions)
                    implementation(rootProject.libs.datetime)
                    implementation(compose.web.core)
                    implementation(compose.runtime)
                    implementation(devNpm("bootstrap", "5.1.3"))
                    implementation(devNpm("bootstrap-icons", "1.8.1"))
                    implementation(devNpm("@fontsource/open-sans", "4.5.0"))
                    implementation(devNpm("@popperjs/core", "2.11.0"))
                    implementation(devNpm("file-loader", "6.2.0"))
                }
            }
        }
    }
}

project(":kjob-api") {
    apply(from = "../gradle/publishing.gradle.kts")
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
    apply(from = "../gradle/publishing.gradle.kts")
    dependencies {
        implementation(rootProject.libs.coroutines.core)
        api(rootProject.libs.slf4j)

        testImplementation(rootProject.libs.kotest.runner)
        testImplementation(rootProject.libs.kotest.assertions)
        testImplementation(rootProject.libs.mockk)
        testRuntimeOnly(rootProject.libs.logback)
    }
}