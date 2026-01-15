import java.security.MessageDigest

plugins {
    `java-library`
    `maven-publish`
}

description = "Command-line interface for Flamingock audit and issue management operations"

dependencies {
    implementation(project(":core:flamingock-core"))
    implementation(project(":community:flamingock-community"))
    implementation(project(":utils:sql-util"))
    implementation(project(":core:target-systems:mongodb-external-system-api"))

    // CLI framework
    implementation("info.picocli:picocli:4.7.5")

    // YAML configuration
    implementation("org.yaml:snakeyaml:2.2")

    // JSON formatting
    implementation("com.google.code.gson:gson:2.10.1")

    // Database clients (community edition)
    implementation("org.mongodb:mongodb-driver-sync:4.9.1")
    implementation("software.amazon.awssdk:dynamodb:2.20.0")
    implementation ("com.couchbase.client:java-client:3.7.3")

    // SQL drivers
    implementation("mysql:mysql-connector-java:8.0.33")
    implementation("com.microsoft.sqlserver:mssql-jdbc:12.4.2.jre8")
    implementation("com.oracle.database.jdbc:ojdbc8:21.9.0.0")
    implementation("org.postgresql:postgresql:42.7.3")
    implementation("org.mariadb.jdbc:mariadb-java-client:3.3.2")
    implementation("com.h2database:h2:2.2.224")
    implementation("org.xerial:sqlite-jdbc:3.41.2.1")
    implementation("com.ibm.informix:jdbc:4.50.10")
    implementation("org.firebirdsql.jdbc:jaybird:4.0.10.java8")

    // HikariCP for SQL database connection pooling
    implementation("com.zaxxer:HikariCP:3.4.5")

    // SLF4J API - needed for interface compatibility (provided by flamingock-core)
    // implementation("org.slf4j:slf4j-api:1.7.36") // Already provided by core dependencies

    // Test dependencies
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.1")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.10.1")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.10.1")
    testImplementation("org.mockito:mockito-core:5.8.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.8.0")
    testImplementation("org.assertj:assertj-core:3.24.2")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter:2.0.2")
    testImplementation("org.testcontainers:testcontainers-mongodb:2.0.2")
    testImplementation("org.testcontainers:testcontainers-couchbase:2.0.2")
    testImplementation("com.github.stefanbirkner:system-lambda:1.2.1")
    // SQL Testcontainers
    testImplementation("org.testcontainers:testcontainers-mysql:2.0.2")
    testImplementation("org.testcontainers:testcontainers-mssqlserver:2.0.2")
    testImplementation("org.testcontainers:testcontainers-oracle-xe:2.0.2")
    testImplementation("org.testcontainers:testcontainers-postgresql:2.0.2")
    testImplementation("org.testcontainers:testcontainers-mariadb:2.0.2")

}

// Create UberJar with all dependencies
val uberJar by tasks.registering(Jar::class) {
    group = "build"
    description = "Create a self-contained JAR with all dependencies"

    archiveBaseName.set("flamingock-cli")
    archiveClassifier.set("uber")
    archiveVersion.set(project.version.toString())
    isZip64 = true

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    manifest {
        attributes["Main-Class"] = "io.flamingock.cli.FlamingockCli"
        attributes["Implementation-Title"] = project.name
        attributes["Implementation-Version"] = project.version
    }

    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) }
    }) {
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    }
}

val createScripts by tasks.registering(CreateStartScripts::class) {
    mainClass.set("io.flamingock.cli.FlamingockCli")
    applicationName = "flamingock"
    outputDir = layout.buildDirectory.dir("scripts").get().asFile
    classpath = files(uberJar.get().archiveFile)
}

val distImage by tasks.registering(Sync::class) {
    group = "distribution"
    description = "Creates the distribution image"
    dependsOn(uberJar, createScripts)

    into(layout.buildDirectory.dir("dist-image"))

    from(createScripts) {
        into("bin")
        fileMode = "755".toInt(8)
    }
    from(uberJar.get().archiveFile) {
        into("lib")
    }
    from("src/dist") {
        into("bin")
    }
}

val distZip by tasks.registering(Zip::class) {
    group = "distribution"
    description = "Builds the ZIP distribution"
    dependsOn(distImage)

    from(distImage.get().destinationDir) {
        into("flamingock-cli")
    }

    archiveBaseName.set("flamingock-cli")
    archiveVersion.set("")
    archiveExtension.set("zip")

    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
}

val distTar by tasks.registering(Tar::class) {
    group = "distribution"
    description = "Builds the TAR distribution"
    dependsOn(distImage)

    from(distImage.get().destinationDir) {
        into("flamingock-cli")
    }

    archiveBaseName.set("flamingock-cli")
    archiveVersion.set("")
    archiveExtension.set("tar.gz")
    compression = Compression.GZIP

    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
}

// Test configuration
tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = false
    }
}

// Debug task - run CLI with debugging enabled
val debugCli by tasks.registering(JavaExec::class) {
    group = "debug"
    description = "Run CLI in debug mode with development logging"

    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("io.flamingock.cli.FlamingockCli")

    // Enable debug logging using CLI flags
    systemProperty("flamingock.debug", "true")

    // JVM debugging
    jvmArgs = listOf(
        "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005",
        "-XX:+ShowCodeDetailsInExceptionMessages"
    )

    // Set working directory to distribution directory
    workingDir = file("${project.rootDir}/flamingock-cli-dist")

    // Default arguments with debug flag (can be overridden)
    args = listOf("--debug", "--help")
}

// Quick test task - run CLI without MongoDB dependency
val testCli by tasks.registering(JavaExec::class) {
    group = "debug"
    description = "Test CLI commands without database connections"

    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("io.flamingock.cli.FlamingockCli")

    systemProperty("flamingock.debug", "true")
    workingDir = file("${project.rootDir}/flamingock-cli-dist")

    args = listOf("--verbose", "--help")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

// Add uber jar as additional artifact to existing maven publication
afterEvaluate {
    publishing {
        publications {
            named<MavenPublication>("maven") {
                // Add the uber jar as additional artifact
                artifact(uberJar.get()) {
                    classifier = "uber"
                }

                // Override description for CLI module
                pom {
                    name.set("Flamingock CLI")
                    description.set("Command-line interface for Flamingock audit and issue management operations")
                }
            }
        }
    }
}

// Generate checksums for distributions
val generateChecksums by tasks.registering {
    group = "distribution"
    description = "Generate checksums for distribution files"
    dependsOn(distZip, distTar)

    doLast {
        val distDir = file("${layout.buildDirectory.get()}/distributions")
        val checksumFile = file("${distDir}/checksums.txt")

        val checksums = mutableListOf<String>()
        distDir.listFiles()?.forEach { distFile ->
            if (distFile.extension in listOf("gz", "zip")) {
                val sha256 = MessageDigest.getInstance("SHA-256")
                distFile.inputStream().use { input ->
                    val buffer = ByteArray(8192)
                    var bytesRead = input.read(buffer)
                    while (bytesRead != -1) {
                        sha256.update(buffer, 0, bytesRead)
                        bytesRead = input.read(buffer)
                    }
                }
                val checksum = sha256.digest().joinToString("") { byte -> "%02x".format(byte) }
                checksums.add("${checksum}  ${distFile.name}")
            }
        }

        checksumFile.writeText(checksums.joinToString("\n"))
        println("Checksums written to: ${checksumFile.absolutePath}")
    }
}

// Ensure distributions are built on every build (neccesary?)
tasks.named("assemble").configure {
    dependsOn(generateChecksums)
}

// Ensure distributions are built before JReleaser tasks
tasks.matching { it.name.startsWith("jreleaser") }.configureEach {
    dependsOn(generateChecksums)
}

// Add task to build all distributions
tasks.register("buildDistributions") {
    group = "distribution"
    description = "Build all distribution archives"
    dependsOn(distZip, distTar, generateChecksums)
}
