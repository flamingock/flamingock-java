
dependencies {
    //Flamingock
    api(project(":core:flamingock-core"))
    implementation(project(":utils:mongodb-util"))
    implementation(project(":legacy:mongock-importer-mongodb"))

    //General
    compileOnly("org.mongodb:mongodb-driver-sync:4.0.0")

    //Test
    testImplementation("org.testcontainers:mongodb:1.18.3")
    testImplementation("org.testcontainers:junit-jupiter:1.18.3")

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