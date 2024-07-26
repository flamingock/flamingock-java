plugins {
    `kotlin-dsl`
    `maven-publish`
    id("java")
}

subprojects {

    apply {
        plugin("org.jetbrains.kotlin.jvm")

        plugin("maven-publish")
    }


    repositories {
        mavenCentral()
        mavenLocal()
    }
    publishing {
        publications {
            create<MavenPublication>("maven") {
                groupId = getGroupId(project)
                artifactId = project.name
                version = "0.0.2-SNAPSHOT"
                from(components["java"])
            }
        }
    }





    val implementation by configurations
    val testImplementation by configurations
    val testRuntimeOnly by configurations

    dependencies {
        implementation(kotlin("stdlib-jdk8"))
        implementation("org.slf4j", "slf4j-api", "2.0.6")

        testImplementation("org.slf4j:slf4j-simple:2.0.6")
        testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.2")
        testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.9.2")
        testImplementation("org.junit.jupiter:junit-jupiter-params:5.9.2")

        testImplementation("org.mockito:mockito-core:4.11.0")
        testImplementation("org.mockito:mockito-junit-jupiter:4.11.0")
        testImplementation("org.mockito:mockito-inline:4.11.0")

    }
    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        testLogging {
            events(
                    org.gradle.api.tasks.testing.logging.TestLogEvent.PASSED,
                    org.gradle.api.tasks.testing.logging.TestLogEvent.SKIPPED,
                    org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED,
                    org.gradle.api.tasks.testing.logging.TestLogEvent.STANDARD_OUT,
            )
        }
    }


    tasks.getByName<Test>("test") {
        useJUnitPlatform()
    }

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(8))
        }
    }
}


fun getGroupId(project: Project) : String{
    val relativeProjectDir = project.projectDir.canonicalPath
        .substring(rootProject.projectDir.canonicalPath.length + 1)
    return if(relativeProjectDir.startsWith("community")) "io.flamingock.community" else "io.flamingock"
}

