dependencies {
    implementation(project(":core:flamingock-core"))
    implementation(project(":utils:sql-util"))
    implementation(project(":utils:test-util"))
}

description = "SQL TestKit for Flamingock testing"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}
