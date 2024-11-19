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
    api(project(":utils"))
    api(project(":flamingock-core-api"))

    implementation("org.mongodb:mongodb-driver-sync:4.3.3")
    implementation(project(":flamingock-core"))

    api("org.apache.httpcomponents:httpclient:4.5.14")
    
    api("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
    

    testImplementation(project(":utils-test"))
}
