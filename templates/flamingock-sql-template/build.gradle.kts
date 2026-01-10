dependencies {
    implementation(project(":core:flamingock-core-commons"))

    testAnnotationProcessor(project(":core:flamingock-processor"))
    testImplementation(project(":community:flamingock-auditstore-sql"))
    testImplementation("com.zaxxer:HikariCP:4.0.3")
    testImplementation("com.h2database:h2:2.1.214")
}

description = "SQL change templates for declarative database schema and data changes"

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
