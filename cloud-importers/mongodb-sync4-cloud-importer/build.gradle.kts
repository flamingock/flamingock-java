dependencies {
    implementation(project(":utils"))
    testImplementation(project(":utils-test"))

    implementation(project(":flamingock-core-api"))
    implementation(project(":cloud-importers:importer-common"))

    implementation("org.mongodb:mongodb-driver-sync:4.3.3")

    testImplementation(project(":flamingock-core"))

    testImplementation("org.testcontainers:mongodb:1.18.3")
    testImplementation("org.testcontainers:junit-jupiter:1.18.3")

    testImplementation("io.mongock:mongock-standalone:5.5.0")
    testImplementation("io.mongock:mongodb-sync-v4-driver:5.5.0")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}
