import java.net.URL
import java.util.Base64

apply(plugin = "maven-publish")
apply(plugin = "signing")
apply(plugin = "org.jetbrains.dokka")

System.getenv("GITHUB_REF")?.let { ref ->
    if (ref.startsWith("refs/tags/v")) {
        version = ref.substringAfterLast("refs/tags/v")
    }
}

val mavenUrl: String by extra
val mavenSnapshotUrl: String by extra
val signingKey: String? by project
val signingPassword: String? by project
val sonatypeUsername: String? by project
val sonatypePassword: String? by project
val sonatypeStagingProfile: String? by project
val pomProjectUrl: String by project
val pomProjectDescription: String by project
val pomScmUrl: String by project
val pomDeveloperId: String by project
val pomDeveloperName: String by project
val pomLicenseName: String by project
val pomLicenseUrl: String by project
val pomLicenseDistribution: String by project

task<Jar>("javadocJar") {
    archiveClassifier.set("javadoc")
}

val repositoryId by lazy {
    if (!rootProject.extra.has("publishRepositoryId")) {
        val id = URL("${mavenUrl}profiles/$sonatypeStagingProfile/start").openConnection().run {
            doOutput = true
            val auth = Base64.getEncoder().encode("$sonatypeUsername:$sonatypePassword".toByteArray())
            setRequestProperty("Authorization", "Basic ${auth.decodeToString()}")
            setRequestProperty("Content-Type", "application/xml")
            getOutputStream().write(
                """
                <promoteRequest>
                    <data>
                        <description>Repository for ${version}</description>
                    </data>
                </promoteRequest>
                """.trimIndent().toByteArray()
            )
            connect()
            getInputStream()
                .readAllBytes()
                .decodeToString()
                .substringAfter("<stagedRepositoryId>")
                .substringBefore("</stagedRepositoryId>")
        }
        rootProject.extra.set("publishRepositoryId", id)
    }
    rootProject.extra.get("publishRepositoryId")
}

configure<PublishingExtension> {
    components.findByName("java")?.also { javaComponent ->
        task<Jar>("sourcesJar") {
            archiveClassifier.set("sources")
            val sourceSets = project.extensions.getByName<SourceSetContainer>("sourceSets")
            from(sourceSets["main"].allSource)
        }
        publications.create<MavenPublication>("mavenJava") {
            from(javaComponent)
            artifact(tasks["sourcesJar"])
        }
    }
    publications.withType<MavenPublication> {
        artifact(tasks.named("javadocJar"))
        with(pom) {
            name.set(rootProject.name)
            url.set(pomProjectUrl)
            description.set(pomProjectDescription)
            scm {
                url.set(pomScmUrl)
            }
            developers {
                developer {
                    id.set(pomDeveloperId)
                    name.set(pomDeveloperName)
                }
            }
            licenses {
                license {
                    name.set(pomLicenseName)
                    url.set(pomLicenseUrl)
                    distribution.set(pomLicenseDistribution)
                }
            }
        }
    }
    repositories {
        maven {
            url = if (version.toString().endsWith("SNAPSHOT")) {
                uri(mavenSnapshotUrl)
            } else {
                uri("${mavenUrl}deployByRepositoryId/$repositoryId")
            }
            credentials {
                username = sonatypeUsername
                password = sonatypePassword
            }
        }
    }
}

configure<SigningExtension> {
    isRequired = !version.toString().endsWith("SNAPSHOT")
    useInMemoryPgpKeys(signingKey, signingPassword)
    sign((extensions["publishing"] as PublishingExtension).publications)
}