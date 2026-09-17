dependencies {
    implementation(project(":core:flamingock-core"))
    implementation(project(":utils:mongodb-util"))
    api(project(":utils:test-util"))

    compileOnlyApi("org.mongodb:mongodb-driver-sync:4.0.0")
}

description = "MongoDB TestKit for Flamingock testing"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}
