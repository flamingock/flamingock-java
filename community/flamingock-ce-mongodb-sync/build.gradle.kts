import org.jetbrains.kotlin.gradle.utils.extendsFrom

dependencies {
    implementation(project(":utils:mongodb-util"))
    implementation(project(":core:flamingock-core"))

    api(project(":transactioners:mongodb-sync-transactioner"))
    api(project(":community:flamingock-ce-commons"))

    compileOnly("org.mongodb:mongodb-driver-sync:4.0.0")


    testImplementation(project(":utils:test-util"))
    testImplementation("org.testcontainers:mongodb:1.18.3")
    testImplementation("org.testcontainers:junit-jupiter:1.18.3")
    testImplementation("org.mockito:mockito-inline:4.11.0")

    testImplementation("io.mongock:mongock-standalone:5.5.0")
    testImplementation("io.mongock:mongodb-sync-v4-driver:5.5.0")
}

description = "${project.name}'s description"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}

configurations.testImplementation {
    extendsFrom(configurations.compileOnly.get())
}