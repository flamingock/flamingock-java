val versions = mapOf(
    "spring" to "[5.0.0,6.0.0)",
    "springBoot" to "[2.0.0,3.0.0)"
)

dependencies {
    api(project(":core:flamingock-core"))
    compileOnly("org.springframework:spring-context:${versions["spring"]}")
    compileOnly("org.springframework.boot:spring-boot:${versions["springBoot"]}")
    compileOnly("org.springframework.boot:spring-boot-autoconfigure:${versions["springBoot"]}")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor:${versions["springBoot"]}")

    testImplementation("org.springframework:spring-context:5.+")
}

description = "Spring Boot v2.x integration module for Flamingock, providing seamless configuration and autoconfiguration capabilities for Spring-based applications"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}

configurations.testImplementation {
    extendsFrom(configurations.compileOnly.get())
}