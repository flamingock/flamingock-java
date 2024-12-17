dependencies {
    api(project(":flamingock-core"))


    testImplementation("com.mysql:mysql-connector-j:8.2.0")
    testImplementation("org.testcontainers:mysql:1.19.3")


    testImplementation(project(":utils-test"))
}

description = "${project.name}'s description"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}