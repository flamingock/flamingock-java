dependencies {

    api(project(":cloud:flamingock-cloud-api"))
    api(project(":core:flamingock-core"))
    implementation(project(":core:flamingock-processor"))

    implementation("com.github.tomakehurst:wiremock-jre8:2.35.2")
    api("com.fasterxml.jackson.core:jackson-databind:2.16.0")
    
    // JUnit for assertion utilities
    implementation("org.junit.jupiter:junit-jupiter-api:5.9.2")
    implementation("org.mockito:mockito-inline:4.11.0")

}

description = "Testing utilities and mock implementations for Flamingock unit and integration tests"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}
