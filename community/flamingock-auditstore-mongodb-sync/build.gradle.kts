import org.jetbrains.kotlin.gradle.utils.extendsFrom

dependencies {
    implementation(project(":utils:mongodb-util"))
    implementation(project(":core:flamingock-core"))

    api(project(":core:target-systems:mongodb-sync-target-system"))
//    api(project(":community:flamingock-community"))

    compileOnly("org.mongodb:mongodb-driver-sync:4.0.0")


    testImplementation(project(":utils:test-util"))
    testImplementation(project(":core:target-systems:nontransactional-target-system"))
    testImplementation(project(":e2e:core-e2e"))
    testImplementation("org.testcontainers:testcontainers-mongodb:2.0.2")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter:2.0.2")
    testImplementation("org.mockito:mockito-inline:4.11.0")

    testImplementation("io.mongock:mongock-standalone:5.5.0")
    testImplementation("io.mongock:mongodb-sync-v4-driver:5.5.0")
}

description = "MongoDB audit store implementation using synchronous MongoDB driver"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}

configurations.testImplementation {
    extendsFrom(configurations.compileOnly.get())
}