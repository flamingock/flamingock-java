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
    api(project(":local-drivers:driver-common"))
    api(project(":local-drivers:couchbase:couchbase-driver"))

    implementation("org.springframework.data:spring-data-couchbase:4.4.8")
    implementation("com.couchbase.client:java-client:3.4.4")

    compileOnly("org.springframework.boot:spring-boot-autoconfigure:2.7.12")

    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor:2.7.12")
}
