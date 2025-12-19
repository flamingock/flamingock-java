dependencies {
    implementation(project(":core:flamingock-core"))


    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // Add test utilities from the repository so tests can use InMemoryTestKit and pipeline helpers
    testImplementation(project(":utils:test-util"))
    testImplementation(project(":core:target-systems:nontransactional-target-system"))
    api("org.mockito:mockito-inline:4.11.0")
}

description = "Test support module for Flamingock framework"


tasks.withType<JavaCompile>().configureEach {
    if (name.contains("Test", ignoreCase = true)) {
        options.compilerArgs.addAll(listOf(
            "-Asources=${projectDir}/src/test/java",
            "-Aresources=${projectDir}/src/test/resources"
        ))
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}