
dependencies {
    //Flamingock
    api(project(":core:flamingock-core"))
    implementation(project(":utils:mongodb-util"))
    implementation(project(":legacy:mongock-importer-mongodb"))
    api(project(":core:target-systems:mongodb-target-system-api"))

    //General
    compileOnly("org.mongodb:mongodb-driver-sync:4.0.0")

    //Test
    testImplementation("org.testcontainers:testcontainers-mongodb:2.0.2")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter:2.0.2")

    testImplementation(project(":cloud:flamingock-cloud"))
    testImplementation(project(":utils:test-util"))
}

description = "MongoDB synchronous driver target system for database change operations"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}

configurations.testImplementation {
    extendsFrom(configurations.compileOnly.get())
}