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
    "flamingock-graalvm"
)

val cloudProjects = setOf(
    "flamingock-cloud",
    "flamingock-cloud-bom"
)

val communityProjects = setOf(
    "flamingock-ce-bom",
    "flamingock-ce-commons",
    "flamingock-ce-mongodb-sync",
    "flamingock-ce-mongodb-springdata-v3-legacy",
    "flamingock-ce-mongodb-springdata",
    "flamingock-ce-couchbase",
    "flamingock-ce-dynamodb",
    "flamingock-importer"
)

val pluginProjects = setOf(
    "flamingock-springboot-integration-v2-legacy",
    "flamingock-springboot-integration"
)

val transactionerProjects = setOf(
    "sql-transactioner",
    "mongodb-sync-target-system",
    "dynamodb-transactioner"
)

val templateProjects = setOf(
    "flamingock-sql-template",
    "flamingock-mongodb-sync-template"
)

val utilProjects = setOf(
    "general-util",
    "test-util",
    "mongodb-util",
    "dynamodb-util"
)

val allProjects = coreProjects + cloudProjects + communityProjects + pluginProjects + transactionerProjects + templateProjects + utilProjects

// Project classification utilities
fun Project.isBomModule(): Boolean = name.endsWith("-bom")
fun Project.isLibraryModule(): Boolean = name !in setOf("flamingock-community-bom", "flamingock-cloud-bom", "flamingock-ce-bom")

// Module category lookup
fun Project.getProjectCategory(): String? = when (name) {
    in coreProjects -> "core"
    in cloudProjects -> "cloud" 
    in communityProjects -> "community"
    in pluginProjects -> "plugins"
    in transactionerProjects -> "transactioners"
    in templateProjects -> "templates"
    in utilProjects -> "utils"
    else -> null
}

// Module bundle utilities
fun getProjectsForBundle(bundle: String?): Set<String> = when (bundle) {
    "core" -> coreProjects
    "cloud" -> cloudProjects
    "community" -> communityProjects
    "plugins" -> pluginProjects
    "transactioners" -> transactionerProjects
    "templates" -> templateProjects
    "utils" -> utilProjects
    "all" -> allProjects
    else -> setOf()
}

// Make variables available to other plugins
extra["coreProjects"] = coreProjects
extra["cloudProjects"] = cloudProjects
extra["communityProjects"] = communityProjects
extra["pluginProjects"] = pluginProjects
extra["transactionerProjects"] = transactionerProjects
extra["templateProjects"] = templateProjects
extra["utilProjects"] = utilProjects
extra["allProjects"] = allProjects

// Apply appropriate plugins based on project type
when {
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