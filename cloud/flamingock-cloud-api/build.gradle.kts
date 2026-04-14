dependencies {
}

description = "Cloud Edition public API definitions"

val coreApiVersion: String by extra
dependencies {
    api("io.flamingock:flamingock-core-api:${coreApiVersion}")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}
