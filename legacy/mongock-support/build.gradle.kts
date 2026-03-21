val coreApiVersion: String by extra
dependencies {
    api(project(":core:flamingock-core"))//todo implementation
    api("io.flamingock:flamingock-core-api:${coreApiVersion}")//todo remove. This should be imported by core
}

description = "Provides a compatibility layer for Mongock-based applications, including drop-in annotations and audit store migration utilities to Flamingock."

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}
