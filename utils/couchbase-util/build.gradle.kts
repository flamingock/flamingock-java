dependencies {
    implementation(project(":core:flamingock-core"))
    compileOnly("com.couchbase.client:java-client:3.6.0")
    
    testImplementation(project(":utils:test-util"))
    testImplementation("com.couchbase.client:java-client:3.6.0")
}

description = "Couchbase utilities and client helpers for document database operations and testing"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}