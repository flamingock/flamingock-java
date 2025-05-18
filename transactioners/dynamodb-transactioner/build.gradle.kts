dependencies {

    implementation(project(":utils:dynamodb-util"))
    api(project(":core:flamingock-core"))

    implementation("software.amazon.awssdk:dynamodb-enhanced:2.25.28")

    testImplementation("software.amazon.awssdk:url-connection-client:2.24.11")
    testImplementation("com.amazonaws:DynamoDBLocal:1.25.0")

    testImplementation(project(":cloud:flamingock-cloud"))
    testImplementation(project(":utils:test-util"))
}

description = "${project.name}'s description"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}