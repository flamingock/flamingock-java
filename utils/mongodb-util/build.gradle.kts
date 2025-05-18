dependencies {
    implementation(project(":core:flamingock-core"))
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}