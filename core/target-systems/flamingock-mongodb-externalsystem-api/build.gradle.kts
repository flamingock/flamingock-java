dependencies {
    api(project(":core:flamingock-core-commons"))

    // MongoDB driver for storage implementations
    compileOnlyApi("org.mongodb:mongodb-driver-sync:4.0.0")
}

description = "MongoDB external system api"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}
