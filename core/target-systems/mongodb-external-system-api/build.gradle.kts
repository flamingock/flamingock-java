dependencies {
    implementation(project(":core:flamingock-core-api"))

    // MongoDB driver for storage implementations
    compileOnly("org.mongodb:mongodb-driver-sync:4.0.0")
}

description = "MongoDB external system api"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}