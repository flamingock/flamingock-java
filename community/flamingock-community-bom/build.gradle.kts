val sqlVersion: String by extra
val mongodbTemplateVersion: String by extra

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
        api("io.flamingock:flamingock-test-support:${version}")
        api("io.flamingock:flamingock-springboot-integration:${version}")
        api("io.flamingock:flamingock-springboot-test-support:${version}")
        api("io.flamingock:flamingock-graalvm:${version}")
        api("io.flamingock:mongock-support:${version}")
        //SQL
        api("io.flamingock:flamingock-sql-util:${sqlVersion}")
        api("io.flamingock:flamingock-sql-test-util:${sqlVersion}")
        api("io.flamingock:flamingock-sql-template:${sqlVersion}")
        //MONGODB template
        api("io.flamingock:flamingock-mongodb-sync-template:${mongodbTemplateVersion}")
    }
}

description = "Bill of Materials for Community Edition dependency management"
