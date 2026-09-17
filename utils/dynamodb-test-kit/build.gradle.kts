dependencies {
    implementation(project(":core:flamingock-core"))
    implementation(project(":utils:dynamodb-util"))
    api(project(":utils:test-util"))

    compileOnlyApi("software.amazon.awssdk:dynamodb-enhanced:2.25.29")

    implementation("software.amazon.awssdk:url-connection-client:2.24.11")

    compileOnlyApi("org.testcontainers:testcontainers:2.0.2")
    compileOnly("org.testcontainers:testcontainers-junit-jupiter:2.0.2")
}

description = "DynamoDB TestKit for Flamingock testing"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}
