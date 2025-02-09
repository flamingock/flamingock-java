val jacksonVersion = "2.16.0"

dependencies {
    implementation(project(":utils"))
    implementation(project(":flamingock-core"))
    implementation(project(":flamingock-core-api"))
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}