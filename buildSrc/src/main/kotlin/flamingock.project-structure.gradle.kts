/**
 * Flamingock project structure and module classification plugin.
 * Defines module categories and provides utilities for project classification.
 */

// Module categorization
val coreProjects = setOf(
    "flamingock-core",
    "flamingock-core-commons",
    "flamingock-core-api",
    "flamingock-template-api",
    "flamingock-processor",
    "flamingock-graalvm",
    "flamingock-test-support",
    "flamingock-bom"
)

val cloudProjects = setOf(
    "flamingock-cloud"
)

val communityProjects = setOf(
    "flamingock-community-bom",
    "flamingock-community",
    "flamingock-mongodb-sync-auditstore",
    "flamingock-couchbase-auditstore",
    "flamingock-dynamodb-auditstore",
    "flamingock-sql-auditstore",
    "flamingock-importer"
)

val pluginProjects = setOf(
    "flamingock-springboot-integration",
    "flamingock-springboot-test-support"
)

val targetSystemProjects = setOf(
    "flamingock-nontransactional-targetsystem",
    "flamingock-mongodb-sync-targetsystem",
    "flamingock-mongodb-springdata-targetsystem",
    "flamingock-sql-targetsystem",
    "flamingock-dynamodb-targetsystem",
    "flamingock-couchbase-targetsystem"
)

val externalSystemProjects = setOf(
    "flamingock-mongodb-externalsystem-api",
    "flamingock-couchbase-externalsystem-api",
    "flamingock-dynamodb-externalsystem-api",
    "flamingock-sql-externalsystem-api"
)

val utilProjects = setOf(
    "general-util",
    "test-util",
    "mongodb-util",
    "dynamodb-util",
    "couchbase-util"
)

val legacyProjects = setOf(
    "mongock-support",
    "mongock-importer-mongodb",
    "mongock-importer-dynamodb",
    "mongock-importer-couchbase"
)

val testKitsProjects = setOf(
    "mongodb-test-kit",
    "dynamodb-test-kit",
    "sql-test-kit",
    "couchbase-test-kit"
)

val allProjects = coreProjects + cloudProjects + communityProjects + pluginProjects + targetSystemProjects + externalSystemProjects + utilProjects + legacyProjects + testKitsProjects

// Project classification utilities
fun Project.isBomModule(): Boolean = name.endsWith("-bom")
fun Project.isLibraryModule(): Boolean = name !in setOf("flamingock-bom", "flamingock-community-bom")

// Module category lookup
fun Project.getProjectCategory(): String? = when (name) {
    in coreProjects -> "core"
    in cloudProjects -> "cloud"
    in communityProjects -> "community"
    in pluginProjects -> "plugins"
    in targetSystemProjects -> "targetSystems"
    in externalSystemProjects -> "externalSystems"
    in utilProjects -> "utils"
    in legacyProjects -> "legacy"
    in testKitsProjects -> "testKits"
    else -> null
}

// Module bundle utilities
fun getProjectsForBundle(bundle: String?): Set<String> = when (bundle) {
    "core" -> coreProjects
    "cloud" -> cloudProjects
    "community" -> communityProjects
    "plugins" -> pluginProjects
    "targetSystems" -> targetSystemProjects
    "externalSystems" -> externalSystemProjects
    "utils" -> utilProjects
    "legacy" -> legacyProjects
    "testKits" -> testKitsProjects
    "all" -> allProjects
    else -> setOf()
}

// Make variables available to other plugins
extra["coreProjects"] = coreProjects
extra["cloudProjects"] = cloudProjects
extra["communityProjects"] = communityProjects
extra["pluginProjects"] = pluginProjects
extra["targetSystemProjects"] = targetSystemProjects
extra["externalSystemProjects"] = externalSystemProjects
extra["utilProjects"] = utilProjects
extra["legacyProjects"] = legacyProjects
extra["testKitsProjects"] = testKitsProjects
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
