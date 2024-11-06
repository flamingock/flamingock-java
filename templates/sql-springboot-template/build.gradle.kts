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
    implementation(project(":flamingock-springboot-v2-runner"))
    implementation(project(":templates:sql-template"))
    compileOnly("org.springframework.boot:spring-boot:2.7.12")
    compileOnly("org.springframework.boot:spring-boot-autoconfigure:2.7.12")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor:2.7.12")

    testImplementation("org.testcontainers:mongodb:1.18.3")
    testImplementation("org.testcontainers:junit-jupiter:1.18.3")
}
