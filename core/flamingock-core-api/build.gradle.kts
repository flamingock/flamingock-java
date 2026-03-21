val jacksonVersion = "2.16.0"
val templateApiVersion: String by extra
val generalUtilVersion: String by extra
dependencies {
    api("io.flamingock:flamingock-template-api:${templateApiVersion}")
    implementation("io.flamingock:flamingock-general-util:${generalUtilVersion}")
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