val coreApiVersion: String by extra
dependencies {
// Core
    api(project(":core:flamingock-core"))
    api("io.flamingock:flamingock-core-api:${coreApiVersion}")
// target systems
    api(project(":core:target-systems:flamingock-nontransactional-target-system"))
    api(project(":core:target-systems:flamingock-couchbase-target-system"))
    api(project(":core:target-systems:flamingock-dynamodb-target-system"))
    api(project(":core:target-systems:flamingock-mongodb-external-system-api"))
    api(project(":core:target-systems:flamingock-mongodb-springdata-target-system"))
    api(project(":core:target-systems:flamingock-mongodb-sync-target-system"))
    api(project(":core:target-systems:flamingock-sql-target-system"))
// Community
    api(project(":community:flamingock-auditstore-couchbase"))
    api(project(":community:flamingock-auditstore-dynamodb"))
    api(project(":community:flamingock-auditstore-mongodb-sync"))
    api(project(":community:flamingock-auditstore-sql"))
}

description = "Community Edition aggregate module providing self-managed audit capabilities"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}