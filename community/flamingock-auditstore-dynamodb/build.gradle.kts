dependencies {
    implementation(project(":utils:dynamodb-util"))
    implementation(project(":core:flamingock-core"))


    api(project(":core:target-systems:dynamodb-external-system-api"))

    compileOnly("software.amazon.awssdk:dynamodb-enhanced:2.25.29")


    testImplementation(project(":utils:test-util"))
    testImplementation(project(":core:target-systems:dynamodb-target-system"))
    testImplementation(project(":core:target-systems:nontransactional-target-system"))

    testImplementation("software.amazon.awssdk:url-connection-client:2.24.11")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter:2.0.2")
    testImplementation("org.testcontainers:testcontainers:2.0.2")

//    Mongock
    testImplementation("io.mongock:mongock-standalone:5.5.0")
    testImplementation("io.mongock:dynamodb-driver:5.5.0")
}

description = "Amazon DynamoDB audit store implementation for change tracking"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}

configurations.testImplementation {
    extendsFrom(configurations.compileOnly.get())
}
