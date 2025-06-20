dependencies {
    implementation(project(":core:flamingock-core-commons"))
    compileOnly("org.mongodb:mongodb-driver-sync:[3.7.0,6.0.0)")


    testAnnotationProcessor(project(":core:flamingock-processor"))
    testImplementation(project(":community:flamingock-ce-mongodb-sync"))
    testImplementation(project(":utils:test-util"))
    testImplementation(project(":utils:mongodb-util"))
    testImplementation("org.testcontainers:mongodb:1.18.3")
    testImplementation("org.testcontainers:junit-jupiter:1.18.3")
}

description = "${project.name}'s description"

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