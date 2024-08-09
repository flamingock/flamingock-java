plugins {
    java
    id("org.springframework.boot") version "2.7.12"
    id("io.spring.dependency-management") version "1.1.0"
}


val jacksonVersion = "2.16.0"
dependencies {
    implementation(project(":flamingock-core"))
    implementation(project(":cloud-transactioners:sql-cloud-transactioner"))
    implementation("com.mysql:mysql-connector-j:8.2.0")
    implementation(project(":templates:sql-template"))

    implementation(project(":internal:legacy-importer-mongodb"))

    implementation("org.slf4j:slf4j-simple:2.0.6")

    implementation("org.apache.httpcomponents:httpclient:4.5.14")
    implementation("com.fasterxml.jackson.core:jackson-core:$jacksonVersion")
    implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
    implementation("com.fasterxml.jackson.core:jackson-annotations:$jacksonVersion")

    implementation("commons-logging:commons-logging:1.2")

}

tasks.withType<Jar> {
    manifest {
        attributes["Main-Class"] = "io.flamingock.examples.mysql.standaloneMysqlStandaloneApplication"
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(sourceSets.main.get().output)
    dependsOn(configurations.compileClasspath)
    from({
        configurations.compileClasspath.get().filter {
            it.name.endsWith("jar")
        }.map { zipTree(it) }
    })
}