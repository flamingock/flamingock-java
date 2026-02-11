dependencies {
    api(project(":core:flamingock-core"))//todo implementation
    api(project(":core:flamingock-core-api"))//todo remove. This should be imported by core
}

description = "Provides a compatibility layer for Mongock-based applications, including drop-in annotations and audit store migration utilities to Flamingock."

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}
