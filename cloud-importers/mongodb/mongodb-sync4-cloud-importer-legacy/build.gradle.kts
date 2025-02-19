dependencies {
    implementation(project(":utils"))
    implementation(project(":flamingock-core"))
    implementation(project(":flamingock-core-api"))
    implementation(project(":cloud-importers:importer-common"))
    implementation("org.mongodb:mongodb-driver-sync:4.3.3")

    testImplementation(project(":utils-test"))
    testImplementation("io.mongock:mongock-standalone:5.5.0")
    testImplementation("io.mongock:mongodb-sync-v4-driver:5.5.0")
    testImplementation("org.testcontainers:mongodb:1.18.3")
    testImplementation("org.testcontainers:junit-jupiter:1.18.3")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}
