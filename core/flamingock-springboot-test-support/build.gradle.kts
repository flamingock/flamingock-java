val springBootVersion = "2.7.18"

dependencies {
    // Spring Boot Test
    api("org.springframework.boot:spring-boot-test:${springBootVersion}")
    api("org.springframework.boot:spring-boot-test-autoconfigure:${springBootVersion}")

    // Flamingock Core Test Support
    api(project(":core:flamingock-test-support"))

    // Test dependencies
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.springframework.boot:spring-boot-starter:${springBootVersion}")
    testImplementation("org.springframework.boot:spring-boot-starter-test:${springBootVersion}")
    testImplementation("org.springframework.boot:spring-boot-autoconfigure:${springBootVersion}")
    testImplementation("org.mockito:mockito-core")
    testImplementation("org.mockito:mockito-inline")
    testImplementation(project(":core:flamingock-core"))
    testImplementation(project(":core:flamingock-test-support"))
    testImplementation(project(":utils:test-util"))
    testImplementation(project(":core:target-systems:nontransactional-target-system"))
    testImplementation(project(":platform-plugins:flamingock-springboot-integration"))
    testImplementation(project(":core:flamingock-core-api"))
}

description = "Test Springboot support module for Flamingock framework"


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