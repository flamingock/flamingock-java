import org.gradle.kotlin.dsl.get

val versions = mapOf(
    "spring" to "[5.0.0,6.0.0)",
    "springBoot" to "[2.0.0,3.0.0)"
)

dependencies {
    api(project(":core:flamingock-core"))
    compileOnly("org.springframework:spring-context:${versions["spring"]}")
    compileOnly("org.springframework.boot:spring-boot:${versions["springBoot"]}")
    compileOnly("org.springframework.boot:spring-boot-autoconfigure:${versions["springBoot"]}")

    testImplementation("org.springframework:spring-context:5.+")
    testImplementation("org.springframework.boot:spring-boot-starter:2.7.18")
    testImplementation("org.springframework.boot:spring-boot-starter-test:2.7.18")

    testAnnotationProcessor(project(":core:flamingock-processor"))

    testImplementation("org.mongodb:mongodb-driver-sync:4.7.2")

    testImplementation(project(":core:target-systems:mongodb-sync-target-system"))
    testImplementation(project(":community:flamingock-ce-mongodb-sync"))

    testImplementation("org.testcontainers:mongodb:1.19.3")
    testImplementation("org.testcontainers:junit-jupiter:1.19.3")
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