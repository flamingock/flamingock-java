/**
 * Flamingock project structure and module classification plugin.
 * Defines module categories and provides utilities for project classification.
 */

// Module categorization
val coreProjects = setOf(
    "flamingock-core",
    "flamingock-core-commons",
    "flamingock-core-api",
    "flamingock-processor",
    "flamingock-graalvm",
    "flamingock-test-support"
)

val cloudProjects = setOf(
    "flamingock-cloud",
    "flamingock-cloud-bom"
)

val communityProjects = setOf(
    "flamingock-community-bom",
    "flamingock-community",
    "flamingock-auditstore-mongodb-sync",
    "flamingock-auditstore-couchbase",
    "flamingock-auditstore-dynamodb",
    "flamingock-auditstore-sql",
    "flamingock-importer"
)

val pluginProjects = setOf(
    "flamingock-springboot-integration",
    "flamingock-springboot-test-support"
)

val targetSystemProjects = setOf(
    "nontransactional-target-system",
    "mongodb-external-system-api",
    "mongodb-sync-target-system",
    "mongodb-springdata-target-system",
    "sql-target-system",
    "dynamodb-target-system",
    "couchbase-target-system"
)

val templateProjects = setOf(
    "flamingock-sql-template"
)

val utilProjects = setOf(
    "general-util",
    "test-util",
    "mongodb-util",
    "dynamodb-util",
    "couchbase-util",
    "sql-util"
)

val legacyProjects = setOf(
    "mongock-support",
    "mongock-importer-mongodb",
    "mongock-importer-dynamodb",
    "mongock-importer-couchbase"
)

val allProjects = coreProjects + cloudProjects + communityProjects + pluginProjects + targetSystemProjects + templateProjects + utilProjects + legacyProjects

// Project classification utilities
fun Project.isBomModule(): Boolean = name.endsWith("-bom")
fun Project.isLibraryModule(): Boolean = name !in setOf("flamingock-community-bom", "flamingock-cloud-bom", "flamingock-community-bom")

// Module category lookup
fun Project.getProjectCategory(): String? = when (name) {
    in coreProjects -> "core"
    in cloudProjects -> "cloud"
    in communityProjects -> "community"
    in pluginProjects -> "plugins"
    in targetSystemProjects -> "targetSystems"
    in templateProjects -> "templates"
    in utilProjects -> "utils"
    in legacyProjects -> "legacy"
    else -> null
}

// Module bundle utilities
fun getProjectsForBundle(bundle: String?): Set<String> = when (bundle) {
    "core" -> coreProjects
    "cloud" -> cloudProjects
    "community" -> communityProjects
    "plugins" -> pluginProjects
    "targetSystems" -> targetSystemProjects
    "templates" -> templateProjects
    "utils" -> utilProjects
    "legacy" -> legacyProjects
    "all" -> allProjects
    else -> setOf()
}

// Make variables available to other plugins
extra["coreProjects"] = coreProjects
extra["cloudProjects"] = cloudProjects
extra["communityProjects"] = communityProjects
extra["pluginProjects"] = pluginProjects
extra["targetSystemProjects"] = targetSystemProjects
extra["templateProjects"] = templateProjects
extra["utilProjects"] = utilProjects
extra["legacyProjects"] = legacyProjects
extra["allProjects"] = allProjects

// Apply appropriate plugins based on project type
when {
    project == rootProject -> { /* Do not publish root project */ }
    isBomModule() -> {
        apply(plugin = "java-platform")
        apply(plugin = "flamingock.license")
        apply(plugin = "flamingock.publishing")
    }
    isLibraryModule() -> {
        apply(plugin = "flamingock.java-library")
        apply(plugin = "flamingock.license")
        apply(plugin = "flamingock.publishing")
    }
    else -> {
        apply(plugin = "java-library")
        apply(plugin = "flamingock.license")
        apply(plugin = "flamingock.publishing")
    }
}
