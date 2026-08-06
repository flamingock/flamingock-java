val coreApiVersion: String by extra
dependencies {
    api("io.flamingock:flamingock-core-api:${coreApiVersion}")

    //General
    compileOnlyApi("com.couchbase.client:java-client:3.6.0")
}

description = "Couchbase external system api"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}
