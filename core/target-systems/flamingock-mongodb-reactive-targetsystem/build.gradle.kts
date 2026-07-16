dependencies {
    api(project(":core:flamingock-core"))
    implementation(project(":utils:mongodb-util"))
    implementation(project(":utils:mongodb-reactive-util"))
    api(project(":core:target-systems:flamingock-mongodb-reactive-externalsystem-api"))

    compileOnly("org.mongodb:mongodb-driver-reactivestreams:4.0.0")

    testImplementation("org.testcontainers:testcontainers-mongodb:2.0.2")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter:2.0.2")
    testImplementation(project(":cloud:flamingock-cloud"))
    testImplementation(project(":utils:test-util"))
}

description = "MongoDB reactive streams driver target system for database change operations"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}

configurations.testImplementation {
    extendsFrom(configurations.compileOnly.get())
}
