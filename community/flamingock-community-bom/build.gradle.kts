plugins {
    `java-platform`
}

dependencies {
    constraints {
        // Add constraints for BOM managed modules
        api("io.flamingock:flamingock-community:${version}")
        api("io.flamingock:flamingock-auditstore-mongodb-sync:$version")
        api("io.flamingock:flamingock-auditstore-mongodb-springdata:${version}")
        api("io.flamingock:flamingock-auditstore-couchbase:$version")
        api("io.flamingock:flamingock-auditstore-dynamodb:$version")
        api("io.flamingock:flamingock-auditstore-sql:$version")
        api("io.flamingock:flamingock-sql-template:$version")
        api("io.flamingock:flamingock-mongodb-sync-template:${version}")
        api("io.flamingock:flamingock-springboot-integration:${version}")
        api("io.flamingock:flamingock-graalvm:${version}")
    }
}

description = "Bill of Materials for Community Edition dependency management"