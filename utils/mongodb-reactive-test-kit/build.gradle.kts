dependencies {
    implementation(project(":core:flamingock-core"))
    implementation(project(":utils:mongodb-util"))
    implementation(project(":utils:flamingock-reactive-util"))
    api(project(":utils:test-util"))

    compileOnlyApi("org.mongodb:mongodb-driver-reactivestreams:4.0.0")
}

description = "MongoDB reactive streams TestKit for Flamingock testing"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}

configurations.testImplementation {
    extendsFrom(configurations.compileOnly.get())
}
