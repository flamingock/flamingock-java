dependencies {
    api(project(":core:flamingock-core"))

    testImplementation("com.mysql:mysql-connector-j:8.2.0")
    testImplementation("org.testcontainers:mysql:1.19.3")

    testImplementation(project(":cloud:flamingock-cloud"))
    testImplementation(project(":utils:test-util"))
}

description = "${project.name}'s description"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}