@Suppress("DSL_SCOPE_VIOLATION", "UnstableApiUsage")
plugins {
    alias(libs.plugins.jvm)
    alias(libs.plugins.testLogger)
    alias(libs.plugins.dokka)
}

allprojects {
    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "kotlin")
    apply(plugin = "com.adarshr.test-logger")
    apply(from = "../gradle/publishing.gradle.kts")

    kotlin {
        target {
            compilations.all {
                kotlinOptions.jvmTarget = "1.8"
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
        from(sourceSets.test.get())
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

        implementation(rootProject.libs.cronutils) {
            exclude(group = "org.slf4j", module = "slf4j-simple")
        }

        implementation(rootProject.libs.coroutines.core)
        implementation(rootProject.libs.logback)
    }
}

project(":kjob-mongo") {
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

project(":kjob-inmem") {
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

project(":kjob-core") {
    dependencies {
        implementation(rootProject.libs.coroutines.core)
        api(rootProject.libs.slf4j)

        testImplementation(rootProject.libs.kotest.runner)
        testImplementation(rootProject.libs.kotest.assertions)
        testImplementation(rootProject.libs.mockk)
        testRuntimeOnly(rootProject.libs.logback)
    }
}