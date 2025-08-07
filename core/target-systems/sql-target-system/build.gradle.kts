import org.jetbrains.kotlin.gradle.utils.extendsFrom

dependencies {
    //Flamingock
    api(project(":core:flamingock-core"))

    //Test
    testImplementation("org.testcontainers:mysql:1.19.0")
    testImplementation("mysql:mysql-connector-java:8.0.33")
    testImplementation("org.testcontainers:junit-jupiter:1.18.3")

    testImplementation(project(":cloud:flamingock-cloud"))
    testImplementation(project(":utils:test-util"))
    testImplementation("com.zaxxer:HikariCP:4.0.3")
}

description = "${project.name}'s description"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}

configurations.testImplementation {
    extendsFrom(configurations.compileOnly.get())
}