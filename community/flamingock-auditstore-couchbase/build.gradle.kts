dependencies {
    api(project(":core:flamingock-core"))
    api(project(":core:target-systems:couchbase-external-system-api"))
    implementation(project(":utils:couchbase-util"))
    
    compileOnly("com.couchbase.client:java-client:3.6.0")

    testImplementation(project(":core:target-systems:couchbase-target-system"))
    testImplementation("org.testcontainers:testcontainers-couchbase:2.0.2")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter:2.0.2")
}

description = "Couchbase audit store implementation for distributed change auditing"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}

configurations.testImplementation {
    extendsFrom(configurations.compileOnly.get())
}