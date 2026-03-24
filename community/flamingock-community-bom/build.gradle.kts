val sqlVersion: String by extra
val mongodbTemplateVersion: String by extra

plugins {
    `java-platform`
}

dependencies {
    constraints {
        // Add constraints for BOM managed modules
        api("io.flamingock:flamingock-community:${version}")
        api("io.flamingock:flamingock-mongodb-sync-auditstore:$version")
        api("io.flamingock:flamingock-mongodb-springdata-auditstore:${version}")
        api("io.flamingock:flamingock-couchbase-auditstore:$version")
        api("io.flamingock:flamingock-dynamodb-auditstore:$version")
        api("io.flamingock:flamingock-sql-auditstore:$version")
        api("io.flamingock:flamingock-test-support:${version}")
        api("io.flamingock:flamingock-nontransactional-targetsystem:${version}")

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
        api("io.flamingock:flamingock-sql-externalsystem-api:${version}")
        api("io.flamingock:flamingock-sql-targetsystem:${version}")

        //mongodb
        api("io.flamingock:flamingock-mongodb-sync-template:${mongodbTemplateVersion}")
        api("io.flamingock:flamingock-mongodb-externalsystem-api:${version}")
        api("io.flamingock:flamingock-mongodb-sync-targetsystem:${version}")
        api("io.flamingock:flamingock-mongodb-springdata-targetsystem:${version}")

        //dynamodb
        api("io.flamingock:flamingock-dynamodb-externalsystem-api:${version}")
        api("io.flamingock:flamingock-dynamodb-targetsystem:${version}")

        //cocuhabase
        api("io.flamingock:flamingock-couchbase-externalsystem-api:${version}")
        api("io.flamingock:flamingock-couchbase-targetsystem:${version}")
    }
}

description = "Bill of Materials for Community Edition dependency management"
