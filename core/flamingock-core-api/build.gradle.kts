val jacksonVersion = "2.16.0"
dependencies {
    implementation(project(":utils:general-util"))
    api("jakarta.annotation:jakarta.annotation-api:2.1.1")//todo can this be implementation?

    implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
    implementation("com.fasterxml.jackson.core:jackson-core:$jacksonVersion")
}

description = "Public API annotations and interfaces for defining changes, stages, and templates"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}