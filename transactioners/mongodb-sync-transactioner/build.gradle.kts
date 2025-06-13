import org.jetbrains.kotlin.gradle.utils.extendsFrom

dependencies {
    //Flamingock
    api(project(":core:flamingock-core"))
    implementation(project(":utils:mongodb-util"))

    //General
    compileOnly("org.mongodb:mongodb-driver-sync:[3.7.0,6.0.0)")

    //Test
    testImplementation("org.testcontainers:mongodb:1.18.3")
    testImplementation("org.testcontainers:junit-jupiter:1.18.3")

    testImplementation(project(":cloud:flamingock-cloud"))
    testImplementation(project(":utils:test-util"))
}

description = "${project.name}'s description"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}

configurations.testImplementation {
    extendsFrom(configurations.compileOnly.get())
}