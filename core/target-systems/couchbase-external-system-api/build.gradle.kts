dependencies {
    implementation(project(":core:flamingock-core-api"))

    //General
    compileOnly("com.couchbase.client:java-client:3.6.0")
}

description = "Couchbase external system api"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}