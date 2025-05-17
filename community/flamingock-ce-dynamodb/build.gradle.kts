dependencies {
    implementation(project(":commons:dynamodb-utils"))
    api(project(":flamingock-core"))

    implementation("software.amazon.awssdk:dynamodb-enhanced:2.25.28")

    api(project(":transactioners:dynamodb-cloud-transactioner"))

    testImplementation("software.amazon.awssdk:url-connection-client:2.24.11")
    testImplementation("com.amazonaws:DynamoDBLocal:1.25.0")

//    Mongock
    testImplementation("io.mongock:mongock-standalone:5.5.0")
    testImplementation("io.mongock:dynamodb-driver:5.5.0")
}

description = "${project.name}'s description"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}