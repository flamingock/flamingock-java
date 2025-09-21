dependencies {
    api(project(":core:flamingock-core"))
    api(project(":core:target-systems:couchbase-target-system"))
    implementation(project(":utils:couchbase-util"))
    
    compileOnly("com.couchbase.client:java-client:3.6.0")

    testImplementation("org.testcontainers:couchbase:1.21.3")
    testImplementation("org.testcontainers:junit-jupiter:1.21.3")
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