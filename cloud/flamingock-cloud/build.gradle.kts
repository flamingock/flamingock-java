dependencies {
    implementation(project(":core:flamingock-core"))

    testAnnotationProcessor(project(":core:flamingock-processor"))
    testImplementation(project(":utils:test-util"))
    testImplementation(project(":core:target-systems:nontransactional-target-system"))
}

description = "Cloud Edition implementation with advanced governance, observability, and SaaS features"


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