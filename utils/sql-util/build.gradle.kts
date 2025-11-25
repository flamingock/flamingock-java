description = "SQL utilities and dialect helpers for database operations and testing"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}

dependencies {
    implementation("com.zaxxer:HikariCP:3.4.5")
    implementation("org.testcontainers:junit-jupiter:1.19.3")
    // SQL Testcontainers
    implementation("org.testcontainers:mysql:1.19.3")
    implementation("org.testcontainers:mssqlserver:1.19.3")
    implementation("org.testcontainers:oracle-xe:1.19.3")
    implementation("org.testcontainers:postgresql:1.19.3")
    implementation("org.testcontainers:mariadb:1.19.3")
}
