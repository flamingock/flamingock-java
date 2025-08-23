plugins {
    id("java")
}


val jacksonVersion = "2.16.0"
dependencies {

    api("software.amazon.awssdk:dynamodb-enhanced:2.25.28")
    
    // TestKit dependencies
    api(project(":utils:test-util"))
    api(project(":utils:general-util"))
    
    // TestContainers for DynamoDB testing
    api("org.testcontainers:testcontainers:1.19.3")
    api("org.testcontainers:junit-jupiter:1.19.3")

}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}