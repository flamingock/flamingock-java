dependencies {
}

description = "Cloud Edition public API definitions"

val coreApiVersion: String by extra
val jacksonVersion = "2.14.1"
dependencies {
    api("io.flamingock:flamingock-core-api:${coreApiVersion}")
    implementation("com.fasterxml.jackson.core:jackson-annotations:${jacksonVersion}")
    testImplementation("com.fasterxml.jackson.core:jackson-databind:${jacksonVersion}")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}
