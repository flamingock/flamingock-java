dependencies {
    implementation(project(":core:flamingock-core"))
    implementation(project(":utils:test-util"))
    
    // MongoDB driver for storage implementations
    compileOnly("org.mongodb:mongodb-driver-sync:4.0.0")
}

description = "MongoDB-specific utilities and helpers for connection management and document operations"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}