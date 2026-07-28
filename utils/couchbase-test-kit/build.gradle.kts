dependencies {
    implementation(project(":core:flamingock-core"))
    implementation(project(":utils:couchbase-util"))
    api(project(":utils:test-util"))

    compileOnlyApi("com.couchbase.client:java-client:3.6.0")
}

description = "Couchbase TestKit for Flamingock testing"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}
