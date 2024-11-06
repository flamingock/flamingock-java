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
    api(project(":flamingock-core"))


    testImplementation("com.mysql:mysql-connector-j:8.2.0")
    testImplementation("org.testcontainers:mysql:1.19.3")


    testImplementation(project(":utils-test"))
}
