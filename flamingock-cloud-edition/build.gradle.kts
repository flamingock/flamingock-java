dependencies {
    api(project(":flamingock-core"))

    testAnnotationProcessor(project(":flamingock-processor"))
    testImplementation(project(":utils-test"))
}

description = "${project.name}'s description"


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