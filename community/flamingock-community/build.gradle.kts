val coreApiVersion: String by extra
dependencies {
// Core
    api(project(":core:flamingock-core"))
    api("io.flamingock:flamingock-core-api:${coreApiVersion}")
// target systems
    api(project(":core:target-systems:flamingock-nontransactional-targetsystem"))
    api(project(":core:target-systems:flamingock-couchbase-targetsystem"))
    api(project(":core:target-systems:flamingock-dynamodb-targetsystem"))
    api(project(":core:target-systems:flamingock-mongodb-externalsystem-api"))
    api(project(":core:target-systems:flamingock-mongodb-springdata-targetsystem"))
    api(project(":core:target-systems:flamingock-mongodb-sync-targetsystem"))
    api(project(":core:target-systems:flamingock-sql-targetsystem"))
// Community
    api(project(":community:flamingock-couchbase-auditstore"))
    api(project(":community:flamingock-dynamodb-auditstore"))
    api(project(":community:flamingock-mongodb-sync-auditstore"))
    api(project(":community:flamingock-sql-auditstore"))
}

description = "Community Edition aggregate module providing self-managed audit capabilities"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}