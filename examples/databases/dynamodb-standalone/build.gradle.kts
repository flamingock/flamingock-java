dependencies {
    implementation(project(":flamingock-core"))
    implementation(project(":local-drivers:dynamodb:dynamodb-driver"))
    implementation("software.amazon.awssdk:dynamodb-enhanced:2.25.28")
    implementation("org.slf4j:slf4j-simple:2.0.6")

    testImplementation("software.amazon.awssdk:url-connection-client:2.24.11")
    testImplementation("com.amazonaws:DynamoDBLocal:1.25.0")
}

tasks.withType<Jar> {
    manifest {
        attributes["Main-Class"] = "io.flamingock.examples.community.dynamodb.CommunityStandaloneDynamoDBApp"
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(sourceSets.main.get().output)
    dependsOn(configurations.compileClasspath)
    from({
        configurations.compileClasspath.get().filter {
            it.name.endsWith("jar")
        }.map { zipTree(it) }
    })
}