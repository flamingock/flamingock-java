
val springBootVersion = "2.0.0.RELEASE"
val springFrameworkVersion = "5.0.4.RELEASE"
dependencies {
    api(project(":core:flamingock-core"))
    compileOnlyApi("org.springframework:spring-context:${springFrameworkVersion}")
    compileOnlyApi("org.springframework.boot:spring-boot:${springBootVersion}")
    compileOnlyApi("org.springframework.boot:spring-boot-autoconfigure:${springBootVersion}")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor:${springBootVersion}")


    testImplementation(platform("org.springframework.boot:spring-boot-dependencies:${springBootVersion}"))

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

description = "Spring Boot integration module for Flamingock, providing seamless configuration and autoconfiguration capabilities for Spring-based applications. Compatible with JDK 17 and above."

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}

configurations.testImplementation {
    extendsFrom(configurations.compileOnly.get())
}
