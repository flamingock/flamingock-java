plugins {
    `maven-publish`
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            groupId = project.group.toString()
            artifactId = project.name
            version = project.version.toString()
        }
    }
    repositories {
        mavenLocal()
    }
}

dependencies {
    api(project(":local-drivers:mongodb:mongodb-facade"))

    implementation("org.mongodb:mongo-java-driver:3.12.8")
    implementation("org.mongodb:bson:3.12.8")

    testImplementation(project(":flamingock-core"))
    testImplementation("org.testcontainers:mongodb:1.18.3")
    testImplementation("org.testcontainers:junit-jupiter:1.18.3")

//    Mongock
    testImplementation("io.mongock:mongock-standalone:5.5.0")
    testImplementation("io.mongock:mongodb-v3-driver:5.5.0")

}
