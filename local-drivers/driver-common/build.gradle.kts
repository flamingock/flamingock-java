dependencies {
    api(project(":flamingock-core"))
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}

