dependencies {
    implementation(project(":core:flamingock-core"))
    implementation(project(":utils:couchbase-util"))
    implementation(project(":utils:test-util"))

    compileOnly("com.couchbase.client:java-client:3.6.0")
}

description = "MongoDB TestKit for Flamingock testing"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}
