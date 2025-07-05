
val versions = mapOf(
    "spring" to "[6.0.0,7.0.0)",
    "springBoot" to "[3.0.0,4.0.0)"
)

dependencies {
    api(project(":core:flamingock-core"))
    implementation(project(":core:flamingock-core-commons"))
    compileOnly("org.springframework:spring-context:${versions["spring"]}")
    compileOnly("org.springframework.boot:spring-boot:${versions["springBoot"]}")
    compileOnly("org.springframework.boot:spring-boot-autoconfigure:${versions["springBoot"]}")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor:${versions["springBoot"]}")


    testImplementation(platform("org.springframework.boot:spring-boot-dependencies:3.3.0")) // align versions

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

description = "Spring Boot v3.x integration module for Flamingock, providing seamless configuration and autoconfiguration capabilities for Spring-based applications. Compatible with JDK 17 and above."

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

configurations.testImplementation {
    extendsFrom(configurations.compileOnly.get())
}