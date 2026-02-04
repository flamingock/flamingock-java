dependencies {
    implementation(project(":core:flamingock-core"))

    compileOnly("org.mongodb:mongodb-driver-sync:4.0.0")

    testImplementation(project(":utils:test-util"))
}

configurations.testImplementation {
    extendsFrom(configurations.compileOnly.get())
}

description = "MongoDB-specific utilities and helpers for connection management and document operations"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}