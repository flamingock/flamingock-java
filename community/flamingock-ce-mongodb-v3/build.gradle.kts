dependencies {
    implementation(project(":commons:mongodb-facade"))
    implementation(project(":importers:mongodb-importer-sync-v4"))

    implementation(project(":flamingock-core"))

    implementation("org.mongodb:mongo-java-driver:3.12.8")
    implementation("org.mongodb:bson:3.12.8")

    testImplementation("org.testcontainers:mongodb:1.18.3")
    testImplementation("org.testcontainers:junit-jupiter:1.18.3")

//    Mongock
    testImplementation("io.mongock:mongock-standalone:5.5.0")
    testImplementation("io.mongock:mongodb-v3-driver:5.5.0")

}

description = "${project.name}'s description"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}