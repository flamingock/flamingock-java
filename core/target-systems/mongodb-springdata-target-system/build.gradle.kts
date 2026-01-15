import org.jetbrains.kotlin.gradle.utils.extendsFrom


val versions = mapOf(
    "mongodb" to "4.0.0",
    "spring-data" to "3.1.4",
    "springboot" to "2.0.0.RELEASE"
)
dependencies {
    //Flamingock
    api(project(":core:flamingock-core"))
    implementation(project(":utils:mongodb-util"))
    implementation(project(":legacy:mongock-importer-mongodb"))
    api(project(":core:target-systems:mongodb-external-system-api"))

    //General
    compileOnly("org.mongodb:mongodb-driver-sync:${versions["mongodb"]}")
    compileOnly("org.springframework.data:spring-data-mongodb:${versions["spring-data"]}")

    //Test
    testImplementation("org.testcontainers:testcontainers-mongodb:2.0.2")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter:2.0.2")

    testImplementation(project(":cloud:flamingock-cloud"))
    testImplementation(project(":utils:test-util"))
}

description = "MongoDB Spring Data target system for Spring-integrated applications"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}

configurations.testImplementation {
    extendsFrom(configurations.compileOnly.get())
}
