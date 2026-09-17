val sqlVersion: String by extra
dependencies {
    implementation(project(":core:flamingock-core"))
    implementation("io.flamingock:flamingock-sql-util:${sqlVersion}")
    api(project(":utils:test-util"))
}

description = "SQL TestKit for Flamingock testing"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}
