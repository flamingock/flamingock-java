dependencies {
    implementation(project(":utils:mongodb-util"))
    implementation(project(":importers:mongodb-importer-sync-v4"))

    implementation(project(":core:flamingock-core"))

    api(project(":transactioners:mongodb-sync-v4-transactioner"))

    implementation("org.mongodb:mongodb-driver-sync:4.3.3")
    testImplementation("org.testcontainers:mongodb:1.18.3")
    testImplementation("org.testcontainers:junit-jupiter:1.18.3")


    testImplementation("io.mongock:mongock-standalone:5.5.0")
    testImplementation("io.mongock:mongodb-sync-v4-driver:5.5.0")
    testImplementation("org.mockito:mockito-inline:4.11.0")

}

description = "${project.name}'s description"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}