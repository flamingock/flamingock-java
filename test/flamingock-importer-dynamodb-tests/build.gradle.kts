dependencies {

    compileOnly("software.amazon.awssdk:dynamodb-enhanced:2.25.29")

    testImplementation(project(":core:flamingock-importer"))
    testAnnotationProcessor(project(":core:flamingock-processor"))
    testImplementation(project(":community:flamingock-ce-dynamodb"))

    testImplementation(project(":utils:test-util"))

    testImplementation("org.testcontainers:junit-jupiter:1.18.3")
    testImplementation("org.mockito:mockito-inline:4.11.0")
    testImplementation("org.testcontainers:localstack:1.19.7")
    testImplementation("software.amazon.awssdk:dynamodb:2.25.61")

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