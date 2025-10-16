dependencies {
    api(project(":core:flamingock-core"))
    api(project(":core:target-systems:sql-target-system"))

    testImplementation("mysql:mysql-connector-java:8.0.33")
    testImplementation(project(":utils:test-util"))
    testImplementation("org.testcontainers:mysql:1.21.3")
    testImplementation("com.zaxxer:HikariCP:3.4.5")
    testImplementation("org.testcontainers:junit-jupiter:1.21.3")
    testImplementation("com.h2database:h2:2.2.224")
    testImplementation("org.mockito:mockito-inline:4.11.0")
}

description = "SQL audit store implementation for distributed change auditing"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}

configurations.testImplementation {
    extendsFrom(configurations.compileOnly.get())
}