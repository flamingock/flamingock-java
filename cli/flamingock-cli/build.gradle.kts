import java.security.MessageDigest

plugins {
    `java-library`
    `maven-publish`
    application
}

description = "Flamingock CLI"

dependencies {
    implementation(project(":core:flamingock-core"))
    implementation(project(":community:flamingock-community"))
    
    // CLI framework
    implementation("info.picocli:picocli:4.7.5")
    
    // YAML configuration
    implementation("org.yaml:snakeyaml:2.2")
    
    // JSON formatting
    implementation("com.google.code.gson:gson:2.10.1")
    
    // Database clients (community edition)
    implementation("org.mongodb:mongodb-driver-sync:4.9.1")
    implementation("software.amazon.awssdk:dynamodb:2.20.0")
    
    // SLF4J API - needed for interface compatibility (provided by flamingock-core)
    // implementation("org.slf4j:slf4j-api:1.7.36") // Already provided by core dependencies
    
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

// Configure application plugin
application {
    mainClass.set("io.flamingock.cli.FlamingockCli")
    applicationName = "flamingock"
    
    // Use the uber JAR for distributions
    applicationDefaultJvmArgs = listOf("-Xmx512m")
}

// Configure distributions to use uber JAR instead of individual dependencies
distributions {
    main {
        contents {
            // Set duplicate strategy
            duplicatesStrategy = DuplicatesStrategy.EXCLUDE
            
            // Clear default lib directory content
            exclude("lib/*.jar")
            
            // Add uber JAR to lib directory
            from(uberJar) {
                into("lib")
            }
            
            // Add sample configuration
            from("src/dist") {
                into(".")
            }
        }
    }
}

// Ensure uber JAR is built before distributions
tasks.distZip {
    dependsOn(uberJar)
}

tasks.distTar {
    dependsOn(uberJar)
    compression = Compression.GZIP
    archiveExtension.set("tar.gz")
}

tasks.installDist {
    dependsOn(uberJar)
}

// Update start scripts to use uber JAR
tasks.startScripts {
    dependsOn(uberJar)
    classpath = files(uberJar.get().archiveFile)
    
    // Customize script generation
    doLast {
        // Fix Unix script to use the uber JAR
        val unixScript = file("$outputDir/$applicationName")
        val unixText = unixScript.readText()
        unixScript.writeText(unixText.replace(
            "CLASSPATH=\$APP_HOME/lib/.*",
            "CLASSPATH=\$APP_HOME/lib/flamingock-cli.jar"
        ))
        
        // Fix Windows script to use the uber JAR  
        val winScript = file("$outputDir/${applicationName}.bat")
        val winText = winScript.readText()
        winScript.writeText(winText.replace(
            "set CLASSPATH=.*",
            "set CLASSPATH=%APP_HOME%\\lib\\flamingock-cli.jar"
        ))
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
    dependsOn(tasks.distZip, tasks.distTar)
    
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

// Ensure distributions are built before JReleaser tasks
tasks.matching { it.name.startsWith("jreleaser") }.configureEach {
    dependsOn(generateChecksums)
}

// Add task to build all distributions
tasks.register("buildDistributions") {
    group = "distribution"
    description = "Build all distribution archives"
    dependsOn(tasks.distZip, tasks.distTar, generateChecksums)
}