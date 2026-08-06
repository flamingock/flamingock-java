import org.jetbrains.kotlin.gradle.utils.extendsFrom

val versions = mapOf(
    "mongodb" to "4.0.0",
    "spring-data" to "3.1.4"
)

dependencies {
    api(project(":core:flamingock-core"))
    implementation(project(":utils:flamingock-reactive-util"))
    api(project(":core:target-systems:flamingock-mongodb-reactive-externalsystem-api"))
    implementation(project(":legacy:mongock-importer-mongodb-reactive"))

    compileOnlyApi("org.mongodb:mongodb-driver-reactivestreams:${versions["mongodb"]}")
    compileOnlyApi("org.springframework.data:spring-data-mongodb:${versions["spring-data"]}")
    compileOnly("io.projectreactor:reactor-core:3.4.34")

    testImplementation("org.testcontainers:testcontainers-mongodb:2.0.2")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter:2.0.2")
    testImplementation(project(":cloud:flamingock-cloud"))
    testImplementation(project(":utils:test-util"))
    testImplementation(project(":utils:mongodb-util"))
    testImplementation(project(":utils:mongodb-reactive-util"))
}

description = "MongoDB Spring Data reactive target system for Spring-integrated applications"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}

configurations.testImplementation {
    extendsFrom(configurations.compileOnly.get())
}
