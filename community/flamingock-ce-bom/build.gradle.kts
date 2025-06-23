plugins {
    `java-platform`
}

dependencies {
    constraints {
        // Add constraints for BOM managed modules
        api("io.flamingock:flamingock-ce-mongodb-v3:$version")
        api("io.flamingock:flamingock-ce-mongodb-sync-v4:$version")
        api("io.flamingock:flamingock-ce-mongodb-springdata-v2:$version")
        api("io.flamingock:flamingock-ce-mongodb-springdata-v3:$version")
        api("io.flamingock:flamingock-ce-mongodb-springdata-v4:$version")
        api("io.flamingock:flamingock-ce-couchbase:$version")
        api("io.flamingock:flamingock-ce-dynamodb:$version")
        api("io.flamingock:flamingock-sql-template:$version")
        api("io.flamingock:flamingock-mongodb-change-template:$version")
    }
}