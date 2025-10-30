dependencies {
// Core
    implementation(project(":core:flamingock-core"))
    api(project(":core:flamingock-core-api"))
// target systems
    api(project(":core:target-systems:nontransactional-target-system"))
    api(project(":core:target-systems:couchbase-target-system"))
    api(project(":core:target-systems:dynamodb-target-system"))
    api(project(":core:target-systems:mongodb-springdata-target-system"))
    api(project(":core:target-systems:mongodb-sync-target-system"))
    api(project(":core:target-systems:sql-target-system"))
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