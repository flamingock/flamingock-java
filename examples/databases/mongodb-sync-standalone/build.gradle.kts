
val mongodbVersion = "4.3.3"
dependencies {
    implementation(project(":flamingock-core"))
    implementation(project(":local-drivers:mongodb:mongodb-sync-v4-driver"))
    implementation("org.mongodb:mongodb-driver-sync:$mongodbVersion")
    implementation("org.mongodb:mongodb-driver-core:$mongodbVersion")
    implementation("org.mongodb:bson:$mongodbVersion")
    implementation("org.slf4j:slf4j-simple:2.0.6")

    testImplementation("org.testcontainers:mongodb:1.18.3")
    testImplementation("org.testcontainers:junit-jupiter:1.18.3")
}

tasks.withType<Jar> {
    manifest {
        attributes["Main-Class"] = "io.flamingock.examples.community.CommunityStandaloneMongodbSyncApp"
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