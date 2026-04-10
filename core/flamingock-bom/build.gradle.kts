val sqlVersion: String by extra
val mongodbTemplateVersion: String by extra
val coreApiVersion: String by extra
val generalUtilVersion: String by extra
val templateApiVersion: String by extra

plugins {
    `java-platform`
}

dependencies {
    constraints {
        // Flamingock Core
        api("io.flamingock:flamingock-general-util:${generalUtilVersion}")
        api("io.flamingock:flamingock-core-api:${coreApiVersion}")
        api("io.flamingock:flamingock-template-api:${templateApiVersion}")
        api("io.flamingock:flamingock-test-support:${version}")
        api("io.flamingock:flamingock-nontransactional-targetsystem:${version}")

        //Flamingock Cloud
        api("io.flamingock:flamingock-cloud:${version}")
        api("io.flamingock:flamingock-cloud-api:${version}")

        // Flamingock Community
        api("io.flamingock:flamingock-community:${version}")
        api("io.flamingock:flamingock-mongodb-sync-auditstore:$version")
        api("io.flamingock:flamingock-mongodb-springdata-auditstore:${version}")
        api("io.flamingock:flamingock-couchbase-auditstore:$version")
        api("io.flamingock:flamingock-dynamodb-auditstore:$version")
        api("io.flamingock:flamingock-sql-auditstore:$version")

        // Springboot
        api("io.flamingock:flamingock-springboot-integration:${version}")
        api("io.flamingock:flamingock-springboot-test-support:${version}")
        api("io.flamingock:flamingock-graalvm:${version}")

        // Mongock
        api("io.flamingock:mongock-support:${version}")

        // Sql
        api("io.flamingock:flamingock-sql-util:${sqlVersion}")
        api("io.flamingock:flamingock-sql-targetsystem:${version}")
        api("io.flamingock:flamingock-sql-externalsystem-api:${version}")
        api("io.flamingock:flamingock-sql-test-util:${sqlVersion}")
        api("io.flamingock:flamingock-sql-template:${sqlVersion}")

        // Mongodb
        api("io.flamingock:flamingock-mongodb-externalsystem-api:${version}")
        api("io.flamingock:flamingock-mongodb-sync-targetsystem:${version}")
        api("io.flamingock:flamingock-mongodb-springdata-targetsystem:${version}")
        api("io.flamingock:flamingock-mongodb-sync-template:${mongodbTemplateVersion}")

        // Dynamodb
        api("io.flamingock:flamingock-dynamodb-externalsystem-api:${version}")
        api("io.flamingock:flamingock-dynamodb-targetsystem:${version}")

        // Couchbase
        api("io.flamingock:flamingock-couchbase-externalsystem-api:${version}")
        api("io.flamingock:flamingock-couchbase-targetsystem:${version}")
    }
}

description = "Bill of Materials for Flamingock dependency management"
