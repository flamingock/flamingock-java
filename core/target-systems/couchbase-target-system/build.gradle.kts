import org.jetbrains.kotlin.gradle.utils.extendsFrom

dependencies {
    //Flamingock
    api(project(":core:flamingock-core"))
    implementation(project(":utils:couchbase-util"))

    //General
    compileOnly("com.couchbase.client:java-client:3.6.0")

    //Test
    testImplementation("org.testcontainers:couchbase:1.21.3")
    testImplementation("org.testcontainers:junit-jupiter:1.21.3")

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