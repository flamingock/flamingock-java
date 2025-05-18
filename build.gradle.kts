import org.jreleaser.model.Active
import org.jreleaser.model.UpdateSection
import org.json.JSONObject
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.*

fun Project.isLibraryModule(): Boolean = name !in setOf(
    "flamingock-community-bom",
    "flamingock-cloud-bom"
)

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("org.json:json:20210307")
    }
}

plugins {
    `kotlin-dsl`
    `maven-publish`
    id("java-library")
    id("org.jreleaser") version "1.15.0"
}

allprojects {
    group = "io.flamingock"
    version = "0.0.34-beta"

    if (isLibraryModule()) {
        apply(plugin = "org.jetbrains.kotlin.jvm")
    }

    repositories {
        mavenCentral()
    }
}

val coreProjects = setOf(
    "flamingock-core", "flamingock-core-api", "flamingock-springboot-v2-integration",
    "flamingock-springboot-v3-integration", "flamingock-graalvm", "utils"
)

val localDriverProjects = setOf(
    "driver-common", "couchbase-driver", "couchbase-springboot-v2-driver",
    "flamingock-ce-dynamodb", "mongodb-util", "flamingock-ce-mongodb-springdata-v2",
    "flamingock-ce-mongodb-springdata-v3", "flamingock-ce-mongodb-springdata-v4",
    "flamingock-ce-mongodb-sync-v4", "flamingock-ce-mongodb-v3", "mongodb-importer"
)

val templateProjects = setOf("flamingock-sql-template", "flamingock-mongodb-change-template")

val transactionerProjects = setOf(
    "sql-transactioner", "mongodb-sync-v4-transactioner", "dynamodb-transactioner"
)

val allProjects = coreProjects + localDriverProjects + templateProjects + transactionerProjects

val projectNameMaxLength = coreProjects.maxOf { it.length }
val tabWidth = 8
val statusPosition = ((projectNameMaxLength / tabWidth) + 1) * tabWidth
val httpClient: HttpClient = HttpClient.newHttpClient()
val mavenUsername: String? = System.getenv("JRELEASER_MAVENCENTRAL_USERNAME")
val mavenPassword: String? = System.getenv("JRELEASER_MAVENCENTRAL_PASSWORD")
val encodedCredentials: String? = if (mavenUsername != null && mavenPassword != null)
    Base64.getEncoder().encodeToString("$mavenUsername:$mavenPassword".toByteArray()) else null

val module: String? = project.findProperty("module") as String?
val releaseBundle: String? = project.findProperty("releaseBundle") as String?

val projectsToRelease = if (module != null) {
    require(allProjects.contains(module)) { "$module is not within the releasable modules $allProjects" }
    setOf(module)
} else {
    when (releaseBundle) {
        "core" -> coreProjects
        "driver" -> localDriverProjects
        "template" -> templateProjects
        "transactioner" -> transactionerProjects
        "all" -> allProjects
        else -> setOf()
    }
}

jreleaser {
    project {
        description.set("Description should be here")
        inceptionYear.set("2024")
        authors.set(setOf("dieppa"))
    }
    gitRootSearch.set(true)
    release {
        github {
            update {
                enabled.set(true)
                sections.set(setOf(UpdateSection.TITLE, UpdateSection.BODY, UpdateSection.ASSETS))
            }
            prerelease {
                enabled.set(true)
                pattern.set(".*-(beta|snapshot|alpha)\$")
            }
            changelog {
                enabled.set(true)
                formatted.set(Active.ALWAYS)
                sort.set(org.jreleaser.model.Changelog.Sort.DESC)
                links.set(true)
                preset.set("conventional-commits")
                releaseName.set("Release {{tagName}}")
                content.set("""
                    ## Changelog
                    {{changelogChanges}}
                    {{changelogContributors}}
                """.trimIndent())
                categoryTitleFormat.set("### {{categoryTitle}}")
                format.set(
                    """|- {{commitShortHash}} 
                       |{{#commitIsConventional}}
                       |{{#conventionalCommitIsBreakingChange}}:rotating_light: {{/conventionalCommitIsBreakingChange}}
                       |{{#conventionalCommitScope}}**{{conventionalCommitScope}}**: {{/conventionalCommitScope}}
                       |{{conventionalCommitDescription}}
                       |{{#conventionalCommitBreakingChangeContent}} - *{{conventionalCommitBreakingChangeContent}}*{{/conventionalCommitBreakingChangeContent}}
                       |{{/commitIsConventional}}
                       |{{^commitIsConventional}}{{commitTitle}}{{/commitIsConventional}}
                       |{{#commitHasIssues}}, closes{{#commitIssues}} {{issue}}{{/commitIssues}}{{/commitHasIssues}} 
                       |({{commitAuthor}})
                    |""".trimMargin().replace("\n", "").replace("\r", "")
                )
                contributors {
                    enabled.set(true)
                    format.set("- {{contributorName}} ({{contributorUsernameAsLink}})")
                }
            }
        }
    }
}

val isReleasing = getIsReleasing()
if (isReleasing) logger.lifecycle("Release bundle: $releaseBundle")

subprojects {

    apply(plugin = "maven-publish")

    val fromComponentPublishing = if (project.isLibraryModule()) "java" else "javaPlatform"
    val mavenPublication = if (project.isLibraryModule()) "maven" else "communityBom"

    if (project.isLibraryModule()) {
        apply(plugin = "java-library")
    } else {
        apply(plugin = "java-platform")
    }

    publishing {
        publications {
            create<MavenPublication>(mavenPublication) {
                groupId = project.group.toString()
                artifactId = project.name
                version = project.version.toString()
                from(components[fromComponentPublishing])
                pom {
                    name.set(project.name)
                    description.set("Description should be here")
                    url.set("https://github.com/mongock/flamingock-project")
                    inceptionYear.set("2024")
                    licenses {
                        license {
                            name.set("Apache-2.0")
                            url.set("https://spdx.org/licenses/Apache-2.0.html")
                        }
                    }
                    developers {
                        developer {
                            id.set("dieppa")
                            name.set("Antonio Perez Dieppa")
                        }
                    }
                    scm {
                        connection.set("scm:git:https://github.com/mongock/flamingock-project.git")
                        developerConnection.set("scm:git:ssh://github.com:mongock/flamingock-project.git")
                        url.set("https://github.com/mongock/flamingock-project")
                    }
                }
            }
        }
        repositories {
            maven {
                url = layout.buildDirectory.dir("staging-deploy").get().asFile.toURI()
            }
            mavenLocal()
        }
    }

    val tabsPrefix = getTabsPrefix()
    if (isReleasing && project.isReleasable()) {
        if (!project.getIfAlreadyReleasedFromCentralPortal()) {
            logger.lifecycle("${project.name}${tabsPrefix}\uD83D\uDE80 PUBLISHING")
            if (project.isLibraryModule()) {
                java {
                    withSourcesJar()
                    withJavadocJar()
                }
            }

            tasks.register("createStagingDeployFolder") {
                group = "build"
                description = "Creates the staging-deploy folder inside the build directory."
                doLast {
                    val stagingDeployDir = layout.buildDirectory.dir("jreleaser").get().asFile
                    if (!stagingDeployDir.exists()) {
                        stagingDeployDir.mkdirs()
                        println("Created: $stagingDeployDir")
                    }
                }
            }

            tasks.matching { it.name == "publish" }.configureEach {
                finalizedBy("createStagingDeployFolder")
            }

            apply(plugin = "org.jreleaser")

            jreleaser {
                project {
                    description.set("Description should be here")
                    inceptionYear.set("2024")
                    authors.set(setOf("dieppa"))
                }
                signing {
                    active.set(Active.ALWAYS)
                    armored = true
                    enabled = true
                }
                gitRootSearch.set(true)
                release {
                    github {
                        skipRelease.set(true)
                        skipTag.set(true)
                    }
                }
                deploy {
                    maven {
                        mavenCentral {
                            create("sonatype") {
                                active.set(Active.ALWAYS)
                                applyMavenCentralRules.set(true)
                                url.set("https://central.sonatype.com/api/v1/publisher")
                                stagingRepository("build/staging-deploy")
                                maxRetries.set(90)
                                retryDelay.set(20)
                            }
                        }
                    }
                }
            }
        } else {
            logger.lifecycle("${project.name}${tabsPrefix}✅  ALREADY PUBLISHED")
        }
    }

    if (project.isLibraryModule()) {
        val implementation by configurations
        val testImplementation by configurations
        val testRuntimeOnly by configurations

        dependencies {
            implementation(kotlin("stdlib-jdk8"))
            implementation("org.slf4j", "slf4j-api", "2.0.6")

            testImplementation("org.slf4j:slf4j-simple:2.0.6")
            testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.2")
            testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.9.2")
            testImplementation("org.junit.jupiter:junit-jupiter-params:5.9.2")

            testImplementation("org.mockito:mockito-core:4.11.0")
            testImplementation("org.mockito:mockito-junit-jupiter:4.11.0")
            testImplementation("org.mockito:mockito-inline:4.11.0")
        }

        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
            testLogging {
                events(
                    org.gradle.api.tasks.testing.logging.TestLogEvent.PASSED,
                    org.gradle.api.tasks.testing.logging.TestLogEvent.SKIPPED,
                    org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED,
                    org.gradle.api.tasks.testing.logging.TestLogEvent.STANDARD_OUT,
                )
            }
        }

        java {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(8))
            }
        }
    }
}

fun Project.isReleasable(): Boolean = projectsToRelease.contains(name)

fun Project.getIfAlreadyReleasedFromCentralPortal(): Boolean {
    val url = "https://central.sonatype.com/api/v1/publisher/published?namespace=${group}&name=$name&version=$version"
    val request = HttpRequest.newBuilder().uri(URI.create(url))
        .header("accept", "application/json")
        .header("Authorization", "Basic $encodedCredentials").GET().build()

    val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
    logger.debug("${project.name}: response from Maven Publisher[${response.statusCode()}]: ${response.body()}")
    return if (response.statusCode() == 200) {
        val map = JSONObject(response.body()).toMap()
        map["published"] as? Boolean ?: error("Invalid response body: ${response.body()}")
    } else {
        error("Error calling Maven Publisher(status:${response.statusCode()}, body:${response.body()})")
    }
}

fun Project.getTabsPrefix(): String {
    val currentPosition = name.length
    val tabsNeeded = ((statusPosition - currentPosition + tabWidth - 1) / tabWidth) + 1
    return "\t".repeat(tabsNeeded)
}

fun Project.getIsReleasing() =
    gradle.startParameter.taskNames.any { it in listOf("jreleaserFullRelease", "jreleaserDeploy", "publish") }

private val Project.verifyPublicationUrl: String
    get() = "https://repo.maven.apache.org/maven2/${group.toString().replace(".", "/")}/$name/$version/"
