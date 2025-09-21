dependencies {
    implementation(project(":core:flamingock-core-commons"))
}

description = "SQL change templates for declarative database schema and data changes"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}