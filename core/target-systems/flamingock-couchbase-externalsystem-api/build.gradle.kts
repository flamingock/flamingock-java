val coreApiVersion: String by extra
dependencies {
    api(project(":core:flamingock-core-commons"))

    //General
    compileOnly("com.couchbase.client:java-client:3.6.0")
}

description = "Couchbase external system api"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}