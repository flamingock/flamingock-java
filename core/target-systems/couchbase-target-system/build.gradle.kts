import org.jetbrains.kotlin.gradle.utils.extendsFrom

dependencies {
    //Flamingock
    api(project(":core:flamingock-core"))
    implementation(project(":utils:couchbase-util"))
    implementation(project(":legacy:mongock-importer-couchbase"))

    //General
    compileOnly("com.couchbase.client:java-client:3.6.0")

    //Test
    testImplementation("org.testcontainers:testcontainers-couchbase:2.0.2")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter:2.0.2")

    testImplementation(project(":cloud:flamingock-cloud"))
    testImplementation(project(":utils:test-util"))
}

description = "Couchbase target system for document database change operations"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}

configurations.testImplementation {
    extendsFrom(configurations.compileOnly.get())
}