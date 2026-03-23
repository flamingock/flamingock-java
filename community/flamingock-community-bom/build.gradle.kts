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
        api("io.flamingock:flamingock-nontransactional-target-system:${version}")

        //springboot
        api("io.flamingock:flamingock-springboot-integration:${version}")
        api("io.flamingock:flamingock-springboot-test-support:${version}")
        api("io.flamingock:flamingock-graalvm:${version}")

        //mongock
        api("io.flamingock:mongock-support:${version}")

        //SQL
        api("io.flamingock:flamingock-sql-util:${sqlVersion}")
        api("io.flamingock:flamingock-sql-test-util:${sqlVersion}")
        api("io.flamingock:flamingock-sql-template:${sqlVersion}")
        api("io.flamingock:flamingock-sql-external-system-api:${version}")
        api("io.flamingock:flamingock-sql-target-system:${version}")

        //mongodb
        api("io.flamingock:flamingock-mongodb-sync-template:${mongodbTemplateVersion}")
        api("io.flamingock:flamingock-mongodb-external-system-api:${version}")
        api("io.flamingock:flamingock-mongodb-sync-target-system:${version}")
        api("io.flamingock:flamingock-mongodb-springdata-target-system:${version}")

        //dynamodb
        api("io.flamingock:flamingock-dynamodb-external-system-api:${version}")
        api("io.flamingock:flamingock-dynamodb-target-system:${version}")

        //cocuhabase
        api("io.flamingock:flamingock-couchbase-external-system-api:${version}")
        api("io.flamingock:flamingock-couchbase-target-system:${version}")
    }
}

description = "Bill of Materials for Community Edition dependency management"
