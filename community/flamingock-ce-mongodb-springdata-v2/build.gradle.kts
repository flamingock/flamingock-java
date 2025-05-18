dependencies {
    implementation(project(":utils:mongodb-util"))
    implementation(project(":importers:mongodb-importer-v3"))


    implementation(project(":core:flamingock-core"))
    api(project(":community:flamingock-ce-mongodb-v3"))

    implementation("org.springframework.data:spring-data-mongodb:2.2.13.RELEASE")
    implementation("org.mongodb:mongo-java-driver:3.12.8")

    compileOnly("org.springframework.boot:spring-boot-autoconfigure:2.7.12")

    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor:2.7.12")

    testImplementation(project(":core:flamingock-core"))
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