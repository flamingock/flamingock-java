import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.Base64
import org.jreleaser.model.Active

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
    version = "0.0.12-beta"

    apply(plugin = "org.jetbrains.kotlin.jvm")

}

val projectsToRelease = setOf(
    "flamingock-core",
    "flamingock-core-api",//required by flamingock-graalvm
    "flamingock-springboot-v2-runner",
    "flamingock-springboot-v3-runner",
//    "metadata-generator",
    "couchbase-driver",
    "couchbase-springboot-v2-driver",
    "dynamodb-driver",
    "mongodb-springdata-v2-driver",
    "mongodb-springdata-v3-driver",
    "mongodb-springdata-v4-driver",
    "mongodb-sync-v4-driver",
    "mongodb-v3-driver",
    "sql-template",
    "sql-springboot-template",
    "sql-cloud-transactioner"
)


val projectNameMaxLength = projectsToRelease.maxOf { it.length }
val tabWidth = 8 //Usually 8 spaces)
val statusPosition = ((projectNameMaxLength / tabWidth) + 1) * tabWidth

//val client: HttpClient = HttpClient.newHttpClient()
val mavenUsername: String = System.getenv("JRELEASER_MAVENCENTRAL_USERNAME")
val mavenPassword: String = System.getenv("JRELEASER_MAVENCENTRAL_PASSWORD")
val encodedCredentials: String = Base64.getEncoder()
    .encodeToString("$mavenUsername:$mavenPassword".toByteArray())

subprojects {
    apply(plugin = "java-library")

    val tabsPrefix = getTabsPrefix()

    if (project.isReleasable()) {
        if(!project.getIfAlreadyReleasedFromCentralPortal()) {
            logger.lifecycle("${project.name}${tabsPrefix}[ \uD83D\uDE80 PUBLISHING ]")
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

                        //Requires env variable: JRELEASER_GITHUB_TOKEN
                        overwrite.set(true)

                        skipRelease.set(true)
                        changelog {
                            enabled.set(true)
                            formatted.set(Active.ALWAYS)
                            links.set(true)
                            sort.set(org.jreleaser.model.Changelog.Sort.DESC)

                            category {
                                key.set("feat")
                                title.set("🚀 New Features")
                                labels.set(setOf("feat"))
                                order.set(1)
                            }
                            category {
                                key.set("fix")
                                title.set("🐛 Bug Fixes")
                                labels.set(setOf("fix"))
                                order.set(2)
                            }
                            category {
                                key.set("docs")
                                title.set("📚 Documentation")
                                labels.set(setOf("fix"))
                                order.set(3)
                            }
                            category {
                                key.set("chore")
                                title.set("🛠️ Maintenance")
                                labels.set(setOf("chore"))
                                order.set(4)
                            }
                        }
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
                                url.set("https://central.sonatype.com/api/v1/publisher")
                                stagingRepository("build/staging-deploy")
                            }


                        }
                    }
                }
            }
        } else {
            logger.lifecycle("${project.name}${tabsPrefix}[ ✅ ALREADY PUBLISHED ]")
        }
    } else {
        logger.lifecycle("${project.name}${tabsPrefix}[ \uD83D\uDCA4 NOT RELEASABLE ]")
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

fun Project.getIfAlreadyReleasedFromCentralPortal() : Boolean {
    val url = "https://central.sonatype.com/api/v1/publisher/published?namespace=${group}&name=$name&version=$version"
    val request = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .header("accept", "application/json")
        .header("Authorization", "Basic $encodedCredentials")
        .GET()
        .build()

    val response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())
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


fun getTabsPrefix(): String {
    val currentPosition = project.name.length
    val tabsNeeded = ((statusPosition - currentPosition + tabWidth - 1) / tabWidth) + 1
    return "\t".repeat(tabsNeeded)
}


val String.isPalindrome: Boolean
    get() = this == this.reversed()

fun Project.getTabsPrefix(): String {
    val currentPosition = name.length
    val tabsNeeded = ((statusPosition - currentPosition + tabWidth - 1) / tabWidth) + 1
    return "\t".repeat(tabsNeeded)
}