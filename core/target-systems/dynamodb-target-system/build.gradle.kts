dependencies {

    api(project(":core:flamingock-core"))
    api(project(":core:target-systems:dynamodb-external-system-api"))
    implementation(project(":utils:dynamodb-util"))
    implementation(project(":legacy:mongock-importer-dynamodb"))

    compileOnly("software.amazon.awssdk:dynamodb-enhanced:2.25.29")

    testImplementation("software.amazon.awssdk:url-connection-client:2.24.11")
    testImplementation("com.amazonaws:DynamoDBLocal:1.25.0")

    testImplementation(project(":cloud:flamingock-cloud"))
    testImplementation(project(":utils:test-util"))
}

description = "Amazon DynamoDB target system for NoSQL change operations"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}