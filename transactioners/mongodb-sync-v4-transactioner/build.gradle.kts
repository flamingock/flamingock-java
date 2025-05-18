dependencies {
    api(project(":core:flamingock-core"))

    implementation("org.mongodb:mongodb-driver-sync:4.3.3")
    implementation(project(":utils:mongodb-util"))

    testImplementation("org.testcontainers:mongodb:1.18.3")
    testImplementation("org.testcontainers:junit-jupiter:1.18.3")

    testImplementation(project(":cloud:flamingock-cloud"))
    testImplementation(project(":utils:test-util"))
}

description = "${project.name}'s description"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}