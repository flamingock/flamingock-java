plugins {
    id("java")
}


val jacksonVersion = "2.16.0"
dependencies {

    api("software.amazon.awssdk:dynamodb-enhanced:2.25.29")
    
    // TestKit dependencies
    api(project(":utils:test-util"))
    api(project(":utils:general-util"))
    
    // TestContainers for DynamoDB testing
    api("org.testcontainers:testcontainers:2.0.2")
    api("org.testcontainers:testcontainers-junit-jupiter:2.0.2")

}

description = "Amazon DynamoDB utilities and TestContainers support for NoSQL testing and development"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}