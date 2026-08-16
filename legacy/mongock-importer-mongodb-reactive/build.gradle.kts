dependencies {
    implementation(project(":core:flamingock-core-commons"))
    implementation(project(":utils:flamingock-reactive-util"))
    compileOnly("org.mongodb:mongodb-driver-reactivestreams:4.0.0")

    testAnnotationProcessor(project(":core:flamingock-processor"))
    testAnnotationProcessor(project(":legacy:mongock-support"))
    testImplementation(project(":legacy:mongock-support"))
    testImplementation(project(":core:target-systems:flamingock-mongodb-reactive-targetsystem"))
    testImplementation(project(":community:flamingock-mongodb-reactive-auditstore"))
    testImplementation(project(":utils:test-util"))
    testImplementation(project(":utils:mongodb-reactive-test-kit"))

    testImplementation("org.testcontainers:testcontainers-mongodb:2.0.2")

    testImplementation("org.testcontainers:testcontainers-junit-jupiter:2.0.2")
    testImplementation("org.mockito:mockito-inline:4.11.0")

}

description = "A MongoDB migration utility that imports Mongock’s execution history into Flamingock-Community’s audit store for smooth project upgrades — reactive driver variant."


java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}

tasks.test {
    useJUnitPlatform()
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
