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
    "flamingock-cli"
)

val cloudProjects = setOf(
    "flamingock-cloud",
    "flamingock-cloud-bom"
)

val communityProjects = setOf(
    "flamingock-community-bom",
    "flamingock-community",
    "flamingock-auditstore-mongodb-sync",
    "flamingock-auditstore-mongodb-springdata",
    "flamingock-auditstore-couchbase",
    "flamingock-auditstore-dynamodb",
    "flamingock-importer"
)

val pluginProjects = setOf(
    "flamingock-springboot-integration"
)

val transactionerProjects = setOf(
    "sql-target-system",
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
fun Project.isLibraryModule(): Boolean = name !in setOf("flamingock-community-bom", "flamingock-cloud-bom", "flamingock-community-bom")

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