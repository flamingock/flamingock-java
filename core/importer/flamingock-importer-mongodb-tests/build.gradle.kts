dependencies {
    compileOnly("org.mongodb:mongodb-driver-sync:4.0.0")

    testImplementation(project(":core:importer:flamingock-importer"))
    testAnnotationProcessor(project(":core:flamingock-processor"))
    testImplementation(project(":community:flamingock-auditstore-mongodb-sync"))
    testImplementation(project(":templates:flamingock-mongodb-sync-template"))
    testImplementation(project(":utils:test-util"))
    testImplementation(project(":utils:mongodb-util"))

    testImplementation("org.testcontainers:mongodb:1.18.3")

    testImplementation("org.testcontainers:junit-jupiter:1.18.3")
    testImplementation("org.mockito:mockito-inline:4.11.0")
    
}

tasks.test {
    useJUnitPlatform()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}

tasks.withType<JavaCompile>().configureEach {
    if (name.contains("Test", ignoreCase = true)) {
        options.compilerArgs.addAll(listOf(
            "-Asources=${projectDir}/src/test/java",
            "-Aresources=${projectDir}/src/test/resources"
        ))
    }
}
configurations.testImplementation {
    extendsFrom(configurations.compileOnly.get())
}