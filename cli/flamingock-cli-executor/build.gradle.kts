plugins {
    `java-library`
    `maven-publish`
}

description = "Flamingock CLI for executing changes in applications"

val jacksonVersion = "2.16.0"

dependencies {
    // CLI Framework
    implementation("info.picocli:picocli:4.7.5")
    annotationProcessor("info.picocli:picocli-codegen:4.7.5")

    // Core dependencies for response handling
    implementation(project(":core:flamingock-core-commons"))
    implementation(project(":utils:general-util"))
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")

    // Test dependencies
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.2")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.9.2")
    testImplementation("org.mockito:mockito-core:4.11.0")
    testImplementation("org.assertj:assertj-core:3.24.2")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

// Create UberJar with all dependencies
val uberJar by tasks.registering(Jar::class) {
    group = "build"
    description = "Create a self-contained JAR with all dependencies"

    archiveBaseName.set("flamingock-cli-executor")
    archiveClassifier.set("uber")
    archiveVersion.set(project.version.toString())
    isZip64 = true

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    manifest {
        attributes["Main-Class"] = "io.flamingock.cli.executor.FlamingockExecutorCli"
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

// Test configuration
tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = false
    }
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

                // Override description for CLI executor module
                pom {
                    name.set("Flamingock CLI")
                    description.set("Flamingock CLI for executing changes in applications")
                }
            }
        }
    }
}

tasks.named("assemble").configure {
    dependsOn(uberJar)
}
