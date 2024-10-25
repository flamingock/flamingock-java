dependencies {
    api(project(":local-drivers:driver-common"))

    implementation("software.amazon.awssdk:dynamodb-enhanced:2.25.28")

    testImplementation("software.amazon.awssdk:url-connection-client:2.24.11")
    testImplementation("com.amazonaws:DynamoDBLocal:1.25.0")

}
