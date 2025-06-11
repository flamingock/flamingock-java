dependencies {
    implementation(project(":utils:mongodb-util"))
    implementation(project(":importers:mongodb-importer-sync-v4"))
    implementation(project(":core:flamingock-core"))
    api(project(":community:flamingock-ce-mongodb-sync"))


    implementation("org.springframework.boot:spring-boot-starter-data-mongodb:[2.0.0,3.0.0)")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor:[2.0.0,3.0.0)")




    testImplementation(project(":core:flamingock-core"))
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