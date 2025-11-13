dependencies {
    api(project(":core:flamingock-core-api"))
    api(project(":core:flamingock-core"))
}

description = "Provides a compatibility layer for Mongock-based applications, including drop-in annotations and audit store migration utilities to Flamingock."

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}
