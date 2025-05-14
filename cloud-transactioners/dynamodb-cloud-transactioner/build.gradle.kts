dependencies {

    implementation(project(":commons:dynamodb-utils"))
    api(project(":flamingock-core"))

    implementation("software.amazon.awssdk:dynamodb-enhanced:2.25.28")

    testImplementation("software.amazon.awssdk:url-connection-client:2.24.11")
    testImplementation("com.amazonaws:DynamoDBLocal:1.25.0")

    testImplementation(project(":cloud-edition"))
    testImplementation(project(":utils-test"))
}

description = "${project.name}'s description"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}