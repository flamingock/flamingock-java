dependencies {
    api(project(":core:flamingock-core"))
    
    implementation("com.couchbase.client:java-client:3.4.4")

    testImplementation("org.testcontainers:couchbase:1.18.3")
    testImplementation("org.testcontainers:junit-jupiter:1.18.3")
}

description = "${project.name}'s description"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}