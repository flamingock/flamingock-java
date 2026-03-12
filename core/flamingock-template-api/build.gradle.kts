dependencies {
    implementation(project(":utils:general-util"))
}

description = "Public API for creating Flamingock change templates"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}
