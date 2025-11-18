import java.time.Duration

dependencies {
    api(project(":core:flamingock-core"))
    api(project(":core:target-systems:sql-target-system"))
    implementation(project(":utils:sql-util"))

    testImplementation("mysql:mysql-connector-java:8.0.33")
    testImplementation("com.microsoft.sqlserver:mssql-jdbc:12.4.2.jre8")
    testImplementation("com.oracle.database.jdbc:ojdbc8:21.9.0.0")
    testImplementation("org.postgresql:postgresql:42.7.3")
    testImplementation("org.mariadb.jdbc:mariadb-java-client:3.3.2")
    testImplementation("org.testcontainers:mysql:1.21.3")
    testImplementation("org.testcontainers:mssqlserver:1.21.3")
    testImplementation("org.testcontainers:oracle-xe:1.21.3")
    testImplementation("org.testcontainers:postgresql:1.21.3")
    testImplementation("org.testcontainers:mariadb:1.21.3")
    testImplementation(project(":utils:test-util"))
    testImplementation("com.zaxxer:HikariCP:3.4.5")
    testImplementation("org.testcontainers:junit-jupiter:1.21.3")
    testImplementation("com.h2database:h2:2.2.224")
    testImplementation("org.mockito:mockito-inline:4.11.0")
    testImplementation("org.xerial:sqlite-jdbc:3.41.2.1")
    testImplementation("com.ibm.informix:jdbc:4.50.10")
    testImplementation("org.firebirdsql.jdbc:jaybird:4.0.10.java8")
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

tasks.test {
    // CI-specific configuration
    val isCI = System.getenv("CI")?.toBoolean() ?: false
    val enabledDialects = System.getProperty("sql.test.dialects") ?: if (isCI) "mysql" else "mysql,oracle"

    systemProperty("sql.test.dialects", enabledDialects)

    // Parallel execution control
    maxParallelForks = if (isCI) 1 else (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)

    // Timeout for long-running database tests
    if (isCI) {
        timeout.set(Duration.ofMinutes(30))
    }

    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = false
    }
}

