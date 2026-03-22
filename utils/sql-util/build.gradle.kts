description = "SQL utilities and dialect helpers for database operations and testing"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}

dependencies {
    implementation("com.zaxxer:HikariCP:3.4.5")
    implementation("org.testcontainers:testcontainers-junit-jupiter:2.0.2")
    // SQL Testcontainers
    implementation("org.testcontainers:testcontainers-mysql:2.0.2")
    implementation("org.testcontainers:testcontainers-mssqlserver:2.0.2")
    implementation("org.testcontainers:testcontainers-oracle-xe:2.0.2")
    implementation("org.testcontainers:testcontainers-postgresql:2.0.2")
    implementation("org.testcontainers:testcontainers-mariadb:2.0.2")
}
