dependencies {
    implementation(project(":core:flamingock-core"))
    api(project(":community:flamingock-ce-commons"))
    
    compileOnly("com.couchbase.client:java-client:[3.0.0,4.0.0)")

    testImplementation("org.testcontainers:couchbase:1.18.3")
    testImplementation("org.testcontainers:junit-jupiter:1.18.3")
}

description = "${project.name}'s description"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}

configurations.testImplementation {
    extendsFrom(configurations.compileOnly.get())
}