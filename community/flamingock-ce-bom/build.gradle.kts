plugins {
    `java-platform`
}

dependencies {
    constraints {
        // Add constraints for BOM managed modules
        api("io.flamingock:flamingock-ce-mongodb-sync:$version")
        api("io.flamingock:flamingock-ce-mongodb-springdata:${version}")
        api("io.flamingock:flamingock-ce-mongodb-springdata-v3-legacy:$version")
        api("io.flamingock:flamingock-ce-couchbase:$version")
        api("io.flamingock:flamingock-ce-dynamodb:$version")
        api("io.flamingock:flamingock-sql-template:$version")
        api("io.flamingock:flamingock-mongodb-sync-template:${version}")
        api("io.flamingock:flamingock-springboot-integration:${version}")
        api("io.flamingock:flamingock-graalvm:${version}")
    }
}