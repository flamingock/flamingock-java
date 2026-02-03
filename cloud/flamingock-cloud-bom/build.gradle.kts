plugins {
    `java-platform`
}

dependencies {
    constraints {
        // Add constraints for BOM managed modules
        api("io.flamingock:flamingock-cloud:${version}")
        api("io.flamingock:flamingock-test-support:${version}")
        api("io.flamingock:flamingock-sql-template:$version")
        api("io.flamingock:flamingock-mongodb-sync-template:${version}")
        api("io.flamingock:flamingock-springboot-integration:${version}")
        api("io.flamingock:flamingock-springboot-test-support:${version}")
        api("io.flamingock:flamingock-graalvm:${version}")
        api("io.flamingock:mongock-support:${version}")
    }
}

description = "Bill of Materials for Cloud Edition dependency management"
