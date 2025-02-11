dependencies {
    implementation(project(":utils"))
    testImplementation(project(":utils-test"))

    implementation(project(":flamingock-core"))
    implementation(project(":flamingock-core-api"))
    implementation(project(":cloud-importers:importer-common"))

    implementation("software.amazon.awssdk:dynamodb-enhanced:2.25.28")

    testImplementation("software.amazon.awssdk:url-connection-client:2.24.11")
    testImplementation("com.amazonaws:DynamoDBLocal:1.25.0")

    implementation(project(":local-drivers:dynamodb:dynamodb-driver"))
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}
