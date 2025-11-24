plugins {
    id("java")
}

group = "io.flamingock"
version = "0.0.38-beta"

repositories {
    mavenCentral()
}

dependencies {
    compileOnly("com.couchbase.client:java-client:3.6.0")


    testImplementation(project(":core:importer:flamingock-importer"))
    testAnnotationProcessor(project(":core:flamingock-processor"))
    testImplementation(project(":community:flamingock-auditstore-couchbase"))
    testImplementation(project(":utils:couchbase-util"))
    testImplementation(project(":utils:test-util"))

    testImplementation("org.testcontainers:couchbase:1.21.3")
    testImplementation("org.testcontainers:junit-jupiter:1.21.3")
    testImplementation("org.mockito:mockito-inline:4.11.0")
}

description = "Couchbase-specific integration tests for the Flamingock importer tool"

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