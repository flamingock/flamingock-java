plugins {
    `java-library`
}

description = "Flamingock CLI"

dependencies {
    implementation(project(":core:flamingock-core"))
    implementation(project(":community:flamingock-community"))
    
    // CLI framework
    implementation("info.picocli:picocli:4.7.5")
    
    // YAML configuration
    implementation("org.yaml:snakeyaml:2.2")
    
    // Database clients (community edition)
    implementation("org.mongodb:mongodb-driver-sync:4.9.1")
    implementation("software.amazon.awssdk:dynamodb:2.20.0")
    
    // Logging - use logback for better debugging in development (Java 8 compatible versions)
    implementation("ch.qos.logback:logback-classic:1.2.12")
    implementation("org.slf4j:slf4j-api:1.7.36")
    
    // Test dependencies
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.1")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.10.1")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.10.1")
    testImplementation("org.mockito:mockito-core:5.8.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.8.0")
    testImplementation("org.assertj:assertj-core:3.24.2")
    testImplementation("org.testcontainers:junit-jupiter:1.19.3")
    testImplementation("org.testcontainers:mongodb:1.19.3")
    testImplementation("com.github.stefanbirkner:system-lambda:1.2.1")
    
}

// Create UberJar with all dependencies
val uberJar by tasks.registering(Jar::class) {
    group = "build"
    description = "Create a self-contained JAR with all dependencies"
    
    archiveClassifier.set("")
    archiveBaseName.set("flamingock-cli")
    archiveVersion.set("")
    
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
    })
}

// Generate distribution directory with executable scripts
val generateDistribution by tasks.registering {
    group = "build"
    description = "Generate CLI distribution with executable scripts"
    dependsOn(uberJar)
    
    val distDir = file("${project.rootDir}/flamingock-cli-dist")
    
    doLast {
        // Create distribution directory
        distDir.mkdirs()
        
        // Copy uber JAR
        copy {
            from(uberJar.get().archiveFile)
            into(distDir)
        }
        
        // Generate Unix shell script
        val shellScript = file("$distDir/flamingock")
        shellScript.writeText("""#!/bin/bash
DIR="${'$'}(cd "${'$'}(dirname "${'$'}{BASH_SOURCE[0]}")" && pwd)"
java -jar "${'$'}DIR/flamingock-cli.jar" "${'$'}@"
""")
        shellScript.setExecutable(true)
        
        // Generate Windows batch file
        val batFile = file("$distDir/flamingock.bat")
        batFile.writeText("""@echo off
java -jar "%~dp0flamingock-cli.jar" %*
""")
        
        // Copy sample configuration if it doesn't exist
        val sampleConfig = file("$distDir/flamingock.yml")
        if (!sampleConfig.exists()) {
            sampleConfig.writeText("""# Flamingock CLI Configuration
flamingock:
  service-identifier: "flamingock-cli"
  
  # MongoDB Configuration (uncomment to use)
  # audit:
  #   mongodb:
  #     connection-string: "mongodb://localhost:27017"
  #     database: "myapp"
  
  # DynamoDB Configuration (uncomment to use)  
  # audit:
  #   dynamodb:
  #     region: "us-east-1"
  #     # endpoint: "http://localhost:8000"  # Optional for local DynamoDB
""")
        }
        
        println("CLI distribution generated at: ${distDir.absolutePath}")
        println("Available executables:")
        println("  Unix/Linux/macOS: ./flamingock-cli-dist/flamingock")
        println("  Windows: ./flamingock-cli-dist/flamingock.bat")
    }
}

// Build task depends on distribution generation
tasks.build {
    dependsOn(generateDistribution)
}

// Clean task removes distribution directory
tasks.clean {
    doLast {
        val distDir = file("${project.rootDir}/flamingock-cli-dist")
        if (distDir.exists()) {
            distDir.deleteRecursively()
            println("Removed CLI distribution directory")
        }
    }
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
    
    // Enable debug logging
    systemProperty("org.slf4j.simpleLogger.defaultLogLevel", "DEBUG")
    systemProperty("flamingock.debug", "true")
    systemProperty("logback.configurationFile", "logback-debug.xml")
    
    // JVM debugging
    jvmArgs = listOf(
        "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005",
        "-XX:+ShowCodeDetailsInExceptionMessages"
    )
    
    // Set working directory to distribution directory
    workingDir = file("${project.rootDir}/flamingock-cli-dist")
    
    // Default arguments (can be overridden)
    args = listOf("--help")
}

// Quick test task - run CLI without MongoDB dependency  
val testCli by tasks.registering(JavaExec::class) {
    group = "debug"
    description = "Test CLI commands without database connections"
    
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("io.flamingock.cli.FlamingockCli")
    
    systemProperty("flamingock.debug", "true")
    workingDir = file("${project.rootDir}/flamingock-cli-dist")
    
    args = listOf("--help")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}