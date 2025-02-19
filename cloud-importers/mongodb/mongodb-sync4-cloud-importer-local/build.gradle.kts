dependencies {
    implementation(project(":utils"))
    implementation(project(":flamingock-core"))
    implementation(project(":flamingock-core-api"))
    implementation(project(":cloud-importers:importer-common"))
    implementation("org.mongodb:mongodb-driver-sync:4.3.3")

    testImplementation(project(":utils-test"))
    testImplementation(project(":local-drivers:mongodb:mongodb-sync-v4-driver"))
    testImplementation("org.testcontainers:mongodb:1.18.3")
    testImplementation("org.testcontainers:junit-jupiter:1.18.3")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}
