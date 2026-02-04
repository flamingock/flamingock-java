dependencies {
    implementation(project(":core:flamingock-core"))
    implementation(project(":utils:dynamodb-util"))
    implementation(project(":utils:general-util"))
    implementation(project(":utils:test-util"))

    compileOnly("software.amazon.awssdk:dynamodb-enhanced:2.25.29")

    compileOnly("software.amazon.awssdk:url-connection-client:2.24.11")

    compileOnly("org.testcontainers:testcontainers:2.0.2")
    compileOnly("org.testcontainers:testcontainers-junit-jupiter:2.0.2")
}

description = "DynamoDB TestKit for Flamingock testing"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}
