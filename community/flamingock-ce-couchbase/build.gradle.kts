dependencies {
    api(project(":core:flamingock-core"))
    api(project(":community:flamingock-ce-commons"))
    implementation(project(":utils:couchbase-util"))
    
    compileOnly("com.couchbase.client:java-client:3.6.0")

    testImplementation("org.testcontainers:couchbase:1.21.3")
    testImplementation("org.testcontainers:junit-jupiter:1.21.3")
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