plugins {
    `java-platform`
}

dependencies {
    constraints {
        // Add constraints for BOM managed modules
        api("io.flamingock:flamingock-cloud:${version}")
        api("io.flamingock:flamingock-test-support:${version}")

        //target systems
        api("io.flamingock:flamingock-nontransactional-targetsystem:${version}")
        api("io.flamingock:flamingock-mongodb-externalsystem-api:${version}")
        api("io.flamingock:flamingock-mongodb-sync-targetsystem:${version}")
        api("io.flamingock:flamingock-mongodb-springdata-targetsystem:${version}")
        api("io.flamingock:flamingock-sql-externalsystem-api:${version}")
        api("io.flamingock:flamingock-sql-targetsystem:${version}")
        api("io.flamingock:flamingock-dynamodb-externalsystem-api:${version}")
        api("io.flamingock:flamingock-dynamodb-targetsystem:${version}")
        api("io.flamingock:flamingock-couchbase-externalsystem-api:${version}")
        api("io.flamingock:flamingock-couchbase-targetsystem:${version}")
        api("io.flamingock:flamingock-sql-template:$version")
        api("io.flamingock:flamingock-mongodb-sync-template:${version}")
        api("io.flamingock:flamingock-springboot-integration:${version}")
        api("io.flamingock:flamingock-springboot-test-support:${version}")
        api("io.flamingock:flamingock-graalvm:${version}")
        api("io.flamingock:mongock-support:${version}")
    }
}

description = "Bill of Materials for Cloud Edition dependency management"
