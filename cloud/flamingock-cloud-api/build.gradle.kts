dependencies {
}

description = "Cloud Edition public API definitions"

val coreApiVersion: String by extra
val jacksonVersion = "2.14.1"
dependencies {
    api("io.flamingock:flamingock-core-api:${coreApiVersion}")
    testImplementation("com.fasterxml.jackson.core:jackson-databind:${jacksonVersion}")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}
