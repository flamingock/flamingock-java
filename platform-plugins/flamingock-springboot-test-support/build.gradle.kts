val springBootVersion = "2.0.0.RELEASE"
val springFrameworkVersion = "5.0.4.RELEASE"
dependencies {
    api(project(":core:flamingock-test-support"))

    implementation(project(":core:flamingock-core"))
    compileOnly("org.springframework:spring-context:${springFrameworkVersion}")
    compileOnly("org.springframework.boot:spring-boot:${springBootVersion}")
    compileOnly("org.springframework.boot:spring-boot-autoconfigure:${springBootVersion}")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor:${springBootVersion}")

    // Required for @FlamingockSpringBootTest annotation
    implementation("org.springframework.boot:spring-boot-test:${springBootVersion}")
    implementation("org.springframework:spring-test:${springFrameworkVersion}")


    testImplementation(platform("org.springframework.boot:spring-boot-dependencies:${springBootVersion}"))

    testImplementation("org.springframework.boot:spring-boot-starter-test")

    // Annotation processor for @EnableFlamingock in tests
    testAnnotationProcessor(project(":core:flamingock-processor"))

    // Spring Boot integration module
    testImplementation(project(":platform-plugins:flamingock-springboot-integration"))

    // InMemory test utilities
    testImplementation(project(":utils:test-util"))

    // Non-transactional target system for tests
    testImplementation(project(":core:target-systems:nontransactional-target-system"))
}

description = "Spring Boot testing integration module for Flamingock. Compatible with JDK 17 and above."

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}

configurations.testImplementation {
    extendsFrom(configurations.compileOnly.get())
}