/**
 * Flamingock release management plugin.
 * Handles JReleaser configuration, release bundles, and publication checking.
 */

import org.jreleaser.model.Active
import org.jreleaser.model.UpdateSection
import org.json.JSONObject
import java.net.URL
import java.net.HttpURLConnection
import java.util.*

plugins {
    id("org.jreleaser")
}

// Get module structure from project-structure plugin

val allProjects = project.extra["allProjects"] as Set<String>

// Release configuration
val module: String? = project.findProperty("module") as String?
val releaseBundle: String? = project.findProperty("releaseBundle") as String?

val projectsToRelease = if (module != null) {
    require(allProjects.contains(module)) { "$module is not within the releasable modules $allProjects" }
    setOf(module)
} else {
    val coreProjects = project.extra["coreProjects"] as Set<String>
    val cloudProjects = project.extra["cloudProjects"] as Set<String>
    val communityProjects = project.extra["communityProjects"] as Set<String>
    val pluginProjects = project.extra["pluginProjects"] as Set<String>
    val targetSystemProjects = project.extra["targetSystemProjects"] as Set<String>
    val externalSystemProjects = project.extra["externalSystemProjects"] as Set<String>
    val templateProjects = project.extra["templateProjects"] as Set<String>
    val utilProjects = project.extra["utilProjects"] as Set<String>
    val legacyProjects = project.extra["legacyProjects"] as Set<String>

    when (releaseBundle) {
        "core" -> coreProjects
        "cloud" -> cloudProjects
        "community" -> communityProjects
        "plugins" -> pluginProjects
        "targetSystems" -> targetSystemProjects
        "externalSystems" -> externalSystemProjects
        "templates" -> templateProjects
        "utils" -> utilProjects
        "legacy" -> legacyProjects
        "all" -> allProjects
        else -> setOf()
    }
}

// JReleaser configuration
jreleaser {
    project {
        inceptionYear.set("2024")
        authors.set(setOf("dieppa", "osantana", "bercianor", "dfrigolet"))
    }
}
if (project != rootProject) {
    tasks.named("jreleaserRelease") {
        enabled = false
    }
} else {
    tasks.register("copyReleaseFiles") {
        dependsOn(":cli:flamingock-cli:assemble")
        doLast {
            mkdir("build/jreleaser/distributions")
            copy {
                from("cli/flamingock-cli/build/distributions/")
                into("build/jreleaser/distributions/")
            }
        }
    }
    tasks.named("jreleaserRelease") {
        dependsOn("copyReleaseFiles")
    }

    jreleaser {
        project {
            description.set("Flamingock is a Java library that brings Change-as-Code to your applications. It enables you to define and apply versioned, auditable changes to databases, event schemas, and external systems, ensuring safety, synchronization, and governance at runtime")
            inceptionYear.set("2024")
            authors.set(setOf("dieppa", "osantana", "bercianor", "dfrigolet"))
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
                    pattern.set("^(0\\..*|.*-(beta\\.?\\d*|snapshot\\.?\\d*|alpha\\.?\\d*|rc\\.?\\d*|RC\\.?\\d*)\$)")
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
                           | {{#commitIsConventional}}
                           |{{#conventionalCommitIsBreakingChange}}:rotating_light: {{/conventionalCommitIsBreakingChange}}
                           |{{#conventionalCommitScope}}**{{conventionalCommitScope}}**: {{/conventionalCommitScope}}
                           |{{conventionalCommitDescription}}
                           |{{#conventionalCommitBreakingChangeContent}} - *{{conventionalCommitBreakingChangeContent}}*{{/conventionalCommitBreakingChangeContent}}
                           |{{/commitIsConventional}}
                           |{{^commitIsConventional}}{{commitTitle}}{{/commitIsConventional}}
                           |{{#commitHasIssues}}, closes{{#commitIssues}} {{issue}}{{/commitIssues}}{{/commitHasIssues}}
                           |{{#contributorName}} ({{contributorName}}){{/contributorName}}
                        |""".trimMargin().replace("\n", "").replace("\r", "")
                    )
                    contributors {
                        enabled.set(true)
                        format.set("- {{contributorName}} ({{contributorUsernameAsLink}})")
                    }
                }
            }

//            files {
//                active.set(org.jreleaser.model.Active.ALWAYS)
//                artifact {
//                    path.set(layout.buildDirectory.file("jreleaser/distributions/flamingock-cli.zip"))
//                    platform.set("cross-platform")
//                }
//                artifact {
//                    path.set(layout.buildDirectory.file("jreleaser/distributions/flamingock-cli.tar.gz"))
//                    platform.set("unix")
//                }
//                artifact {
//                    path.set(layout.buildDirectory.file("jreleaser/distributions/checksums.txt"))
//                    platform.set("checksums")
//                }
//            }
        }
    }
}

// Release detection and management
val isReleasing = gradle.startParameter.taskNames.any {
    it in listOf("jreleaserFullRelease", "jreleaserDeploy", "publish")
}

if (isReleasing && projectsToRelease.contains(project.name)) {
    if (!project.getIfAlreadyReleasedFromCentralPortal()) {
        val tabsPrefix = project.getTabsPrefix()
        logger.lifecycle("${project.name}${tabsPrefix}\uD83D\uDE80 PUBLISHING")
        apply(plugin = "flamingock.release")
    } else {
        val tabsPrefix = project.getTabsPrefix()
        logger.lifecycle("${project.name}${tabsPrefix}✅  ALREADY PUBLISHED")
    }
}

// Utility functions
fun Project.getIfAlreadyReleasedFromCentralPortal(): Boolean {
    val mavenUsername: String? = System.getenv("JRELEASER_MAVENCENTRAL_USERNAME")
    val mavenPassword: String? = System.getenv("JRELEASER_MAVENCENTRAL_PASSWORD")
    val encodedCredentials: String? = if (mavenUsername != null && mavenPassword != null)
        Base64.getEncoder().encodeToString("$mavenUsername:$mavenPassword".toByteArray()) else null

    val url = "https://central.sonatype.com/api/v1/publisher/published?namespace=${group}&name=$name&version=$version"
    val connection = URL(url).openConnection() as HttpURLConnection
    connection.requestMethod = "GET"
    connection.setRequestProperty("accept", "application/json")
    connection.setRequestProperty("Authorization", "Basic $encodedCredentials")

    val responseCode = connection.responseCode
    val responseBody = if (responseCode == 200) {
        connection.inputStream.bufferedReader().use { it.readText() }
    } else {
        connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
    }

    logger.debug("$name: response from Maven Publisher[$responseCode]: $responseBody")
    return if (responseCode == 200) {
        val map = JSONObject(responseBody).toMap()
        map["published"] as? Boolean ?: error("Invalid response body: $responseBody")
    } else {
        error("Error calling Maven Publisher(status:$responseCode, body:$responseBody)")
    }
}

fun Project.getTabsPrefix(): String {
    val allProjects = project.extra["allProjects"] as Set<String>
    val projectNameMaxLength = allProjects.maxOf { it.length }
    val tabWidth = 8
    val statusPosition = ((projectNameMaxLength / tabWidth) + 1) * tabWidth
    val currentPosition = name.length
    val tabsNeeded = ((statusPosition - currentPosition + tabWidth - 1) / tabWidth) + 1
    return "\t".repeat(tabsNeeded)
}
