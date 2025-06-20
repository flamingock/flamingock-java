
dependencies {
    implementation(project(":core:flamingock-core-commons"))

    compileOnly("org.mongodb:mongodb-driver-sync:[3.7.0, 6.0.0)")
    compileOnly("software.amazon.awssdk:dynamodb-enhanced:[2.0.0,3.0.0)")
    compileOnly("com.couchbase.client:java-client:[3.0.0,4.0.0)")


    testAnnotationProcessor(project(":core:flamingock-processor"))
    testImplementation(project(":community:flamingock-ce-mongodb-sync"))
    testImplementation(project(":templates:flamingock-mongodb-sync-template"))
    testImplementation(project(":utils:test-util"))
    testImplementation("org.testcontainers:mongodb:1.18.3")
    testImplementation("org.testcontainers:junit-jupiter:1.18.3")
    testImplementation("org.mockito:mockito-inline:4.11.0")
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