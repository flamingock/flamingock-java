val coreApiVersion: String by extra
dependencies {
    implementation("io.flamingock:flamingock-core-api:${coreApiVersion}")

    // MongoDB driver for storage implementations
    compileOnly("org.mongodb:mongodb-driver-sync:4.0.0")
}

description = "MongoDB external system api"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}