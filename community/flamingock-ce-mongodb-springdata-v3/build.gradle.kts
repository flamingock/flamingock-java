dependencies {
    implementation(project(":commons:mongodb-facade"))
    implementation(project(":importers:mongodb-importer-sync-v4"))

    implementation(project(":flamingock-core"))
    api(project(":community:flamingock-ce-mongodb-sync-v4"))

    implementation("org.springframework.data:spring-data-mongodb:3.2.12")
    implementation("org.mongodb:mongodb-driver-sync:4.3.3")

    compileOnly("org.springframework.boot:spring-boot-autoconfigure:2.7.12")

    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor:2.7.12")

    testImplementation(project(":flamingock-core"))
    testImplementation("org.testcontainers:mongodb:1.18.3")
    testImplementation("org.testcontainers:junit-jupiter:1.18.3")

//    Mongock
    testImplementation("io.mongock:mongock-standalone:5.5.0")
    testImplementation("io.mongock:mongodb-sync-v4-driver:5.5.0")
}

description = "${project.name}'s description"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}