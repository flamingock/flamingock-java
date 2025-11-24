dependencies {
    implementation(project(":core:flamingock-core-commons"))
    implementation(project(":utils:couchbase-util"))

    //General
    compileOnly("com.couchbase.client:java-client:3.6.0")


    testAnnotationProcessor(project(":core:flamingock-processor"))
    testAnnotationProcessor(project(":legacy:mongock-support"))
    testImplementation(project(":legacy:mongock-support"))
    testImplementation(project(":core:target-systems:couchbase-target-system"))

    testImplementation(project(":community:flamingock-auditstore-couchbase"))
    testImplementation(project(":utils:couchbase-util"))
    testImplementation(project(":utils:test-util"))

    testImplementation("org.testcontainers:couchbase:1.21.3")
    testImplementation("org.testcontainers:junit-jupiter:1.21.3")
    testImplementation("org.mockito:mockito-inline:4.11.0")
}


description = "A Couchbase migration utility that imports Mongock’s execution history into Flamingock-Community’s audit store for smooth project upgrades."


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