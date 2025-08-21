dependencies {
    implementation(project(":core:flamingock-core"))
    compileOnly("com.couchbase.client:java-client:3.6.0")
    
    testImplementation(project(":utils:test-util"))
    testImplementation("com.couchbase.client:java-client:3.6.0")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}