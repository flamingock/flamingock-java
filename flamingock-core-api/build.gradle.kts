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

val jacksonVersion = "2.16.0"
dependencies {
    implementation(project(":utils"))

    implementation("com.fasterxml.jackson.core:jackson-core:$jacksonVersion")
}
