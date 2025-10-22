import org.jetbrains.kotlin.gradle.utils.extendsFrom

dependencies {
    //Flamingock
    api(project(":core:flamingock-core"))
    implementation(project(":utils:sql-util"))

    //Test
    testImplementation("org.testcontainers:testcontainers-mysql:2.0.1")
    testImplementation("org.testcontainers:testcontainers-postgresql:2.0.1")
    testImplementation("org.testcontainers:testcontainers-mssqlserver:2.0.1")
    testImplementation("org.testcontainers:testcontainers-oracle-free:2.0.1")
    testImplementation("mysql:mysql-connector-java:8.0.33")
    testImplementation("org.postgresql:postgresql:42.7.8")
    testImplementation("com.microsoft.sqlserver:mssql-jdbc:13.2.1.jre8")
    testImplementation("com.oracle.database.jdbc:ojdbc8:23.2.0.0")
    testImplementation("org.xerial:sqlite-jdbc:3.50.3.0")
    testImplementation("com.h2database:h2:2.2.220")
    testImplementation("org.hsqldb:hsqldb:2.5.2")
    testImplementation("org.apache.derby:derbytools:10.15.2.0")
    testImplementation("org.firebirdsql.jdbc:jaybird:5.0.10.java8")
    testImplementation("org.testcontainers:junit-jupiter:1.18.3")

    testImplementation(project(":cloud:flamingock-cloud"))
    testImplementation(project(":utils:test-util"))
    testImplementation("com.zaxxer:HikariCP:4.0.3")
}

description = "SQL database target system for relational database change operations"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}

configurations.testImplementation {
    extendsFrom(configurations.compileOnly.get())
}
