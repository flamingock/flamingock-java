import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.Base64
import org.jreleaser.model.Active
import org.jreleaser.model.UpdateSection

import org.json.JSONObject

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
    version = "0.0.25-beta"

    apply(plugin = "org.jetbrains.kotlin.jvm")

}

val coreProjects = setOf(
    "flamingock-core",
    "flamingock-core-api",
    "flamingock-core-template",
    "flamingock-springboot-v2-runner",
    "flamingock-springboot-v3-runner",
    "utils"
)

val localDriverProjects = setOf(
    "driver-common",
    "couchbase-driver",
    "couchbase-springboot-v2-driver",
    "dynamodb-driver",
    "mongodb-facade",
    "mongodb-springdata-v2-driver",
    "mongodb-springdata-v3-driver",
    "mongodb-springdata-v4-driver",
    "mongodb-sync-v4-driver",
    "mongodb-v3-driver"
)

val templateProjects = setOf(
    "sql-template",
    "sql-springboot-template"
)


val transactionerProjects = setOf(
    "sql-cloud-transactioner"
)

val allProjects = coreProjects + localDriverProjects + templateProjects + transactionerProjects


val projectNameMaxLength = coreProjects.maxOf { it.length }
val tabWidth = 8 //Usually 8 spaces)
val statusPosition = ((projectNameMaxLength / tabWidth) + 1) * tabWidth

val httpClient: HttpClient = HttpClient.newHttpClient()
val mavenUsername: String? = System.getenv("JRELEASER_MAVENCENTRAL_USERNAME")
val mavenPassword: String? = System.getenv("JRELEASER_MAVENCENTRAL_PASSWORD")
val encodedCredentials: String? = if (mavenUsername != null && mavenPassword != null) Base64.getEncoder()
    .encodeToString("$mavenUsername:$mavenPassword".toByteArray()) else null



val module: String? = project.findProperty("module") as String?
val releaseBundle: String? = project.findProperty("releaseBundle") as String?

val projectsToRelease = if(module != null) {
    if(allProjects.contains(module)) {
        setOf(module)
    } else {
        throw RuntimeException("$module is not withing the releseable modules $allProjects")
    }
} else {
    when(releaseBundle) {
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
            //Requires env variable: JRELEASER_GITHUB_TOKEN
            update {
                enabled.set(true)
                sections.set(setOf(
                    UpdateSection.TITLE,
                    UpdateSection.BODY,
                    UpdateSection.ASSETS
                ))
            }

            prerelease {
                enabled.set(true)
                pattern.set(".*-(beta|snapshot|alpha)\$")
            }

            changelog {
                enabled.set(true)
                sort.set(org.jreleaser.model.Changelog.Sort.DESC)
                formatted.set(Active.ALWAYS)
                links.set(true)
                sort.set(org.jreleaser.model.Changelog.Sort.DESC)

                releaseName.set("Release {{tagName}}")

                content.set("""
                ## Changelog
                {{changelogChanges}}
                {{changelogContributors}}
                """)

                categoryTitleFormat.set("### {{categoryTitle}}")
                preset.set("conventional-commits")
                format.set("""
                    |- {{commitShortHash}} 
                        |{{#commitIsConventional}}
                            |{{#conventionalCommitIsBreakingChange}}:rotating_light: {{/conventionalCommitIsBreakingChange}}
                            |{{#conventionalCommitScope}}**{{conventionalCommitScope}}**: {{/conventionalCommitScope}}
                            |{{conventionalCommitDescription}}
                            |{{#conventionalCommitBreakingChangeContent}} - *{{conventionalCommitBreakingChangeContent}}*{{/conventionalCommitBreakingChangeContent}}
                        |{{/commitIsConventional}}
                        |{{^commitIsConventional}}{{commitTitle}}{{/commitIsConventional}}
                        |{{#commitHasIssues}}, closes{{#commitIssues}} {{issue}}{{/commitIssues}}{{/commitHasIssues}} 
                        |({{commitAuthor}})
                    |""".trimMargin().replace("\n", "").replace("\r", ""))

                contributors {
                    enabled.set(true)
                    format.set("- {{contributorName}} ({{contributorUsernameAsLink}})")
                }
            }
        }
    }
}


val isReleasing = getIsReleasing()

if(isReleasing) {
    logger.lifecycle("Release bundle: $releaseBundle")
}

subprojects {
    apply(plugin = "java-library")

    val tabsPrefix = getTabsPrefix()
    if (isReleasing) {
        if (project.isReleasable()) {
            if (!project.getIfAlreadyReleasedFromCentralPortal()) {
                logger.lifecycle("${project.name}${tabsPrefix}\uD83D\uDE80 PUBLISHING")
                java {
                    withSourcesJar()
                    withJavadocJar()
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

                apply(plugin = "maven-publish")
                apply(plugin = "org.jreleaser")

                publishing {
                    publications {
                        create<MavenPublication>("maven") {
                            groupId = project.group.toString()
                            artifactId = project.name
                            version = project.version.toString()

                            from(components["java"])

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
                                    connection.set("scm:git:https://github.com:mongock/flamingock-project.git")
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
                    }
                }

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
                        //Requires env variables
                        // JRELEASER_GPG_PUBLIC_KEY
                        // JRELEASER_GPG_SECRET_KEY
                        // JRELEASER_GPG_PASSPHRASE

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
                                //Requires env variables
                                // JRELEASER_MAVENCENTRAL_USERNAME
                                // JRELEASER_MAVENCENTRAL_PASSWORD

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
        } else {
            if(allProjects.contains(project.name)) {
                logger.lifecycle("${project.name}${tabsPrefix}\uD83D\uDCA4 NOT RELEASING")
            } else {
                logger.lifecycle("${project.name}${tabsPrefix}\uD83D\uDEAB NOT RELEASABLE")
            }

        }
    }



    repositories {
        mavenCentral()
        mavenLocal()
    }


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


    tasks.getByName<Test>("test") {
        useJUnitPlatform()
    }

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(8))
        }
    }
}

fun Project.isReleasable(): Boolean = projectsToRelease.contains(name)


fun Project.getIfAlreadyReleasedFromCentralPortal(): Boolean {
    val url = "https://central.sonatype.com/api/v1/publisher/published?namespace=${group}&name=$name&version=$version"
    val request = HttpRequest.newBuilder().uri(URI.create(url)).header("accept", "application/json")
        .header("Authorization", "Basic $encodedCredentials").GET().build()


    val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
    logger.debug("${project.name}: response from Maven Publisher[${response.statusCode()}]: ${response.body()}")
    return if (response.statusCode() == 200) {
        val jsonObject = JSONObject(response.body())
        val map: Map<String, Any> = jsonObject.toMap()
        if (map["published"] != null && map["published"] is Boolean) {
            val isPublished = map["published"] as Boolean
            isPublished
        } else {
            throw RuntimeException("Error parsing response from Maven Publisher: body = ${response.body()})")

        }
    } else {
        //TODO implement retry
        throw RuntimeException("Error calling Maven Publisher(status:${response.statusCode()}, body:${response.body()})")
    }
}


fun Project.getTabsPrefix(): String {
    val currentPosition = name.length
    val tabsNeeded = ((statusPosition - currentPosition + tabWidth - 1) / tabWidth) + 1
    return "\t".repeat(tabsNeeded)
}

fun Project.getIsReleasing() =
    gradle.startParameter.taskNames.contains("jreleaserFullRelease")
            || gradle.startParameter.taskNames.contains("jreleaserDeploy")
            || gradle.startParameter.taskNames.contains("publish")

private val Project.verifyPublicationUrl: String
    get() {
        val groupIdPath = group.toString().replace(".", "/")
        return "https://repo.maven.apache.org/maven2/$groupIdPath/$name/$version/"
    }