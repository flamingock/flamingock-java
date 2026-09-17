import org.jetbrains.kotlin.gradle.utils.extendsFrom

dependencies {
    api(project(":utils:mongodb-util"))
    implementation(project(":utils:mongodb-reactive-util"))
    implementation(project(":utils:flamingock-reactive-util"))
    api(project(":core:flamingock-core"))
    
    api(project(":core:target-systems:flamingock-mongodb-reactive-externalsystem-api"))

    compileOnlyApi("org.mongodb:mongodb-driver-reactivestreams:4.0.0")

    testImplementation(project(":utils:test-util"))
    testImplementation(project(":utils:mongodb-reactive-test-kit"))
    testImplementation(project(":core:target-systems:flamingock-nontransactional-targetsystem"))
    testImplementation(project(":core:target-systems:flamingock-mongodb-reactive-targetsystem"))
    testImplementation("org.testcontainers:testcontainers-mongodb:2.0.2")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter:2.0.2")
    testImplementation("org.mockito:mockito-inline:4.11.0")
    testImplementation("io.mongock:mongock-standalone:5.5.0")
}

description = "MongoDB audit store implementation using reactive streams MongoDB driver"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}

configurations.testImplementation {
    extendsFrom(configurations.compileOnly.get())
}
