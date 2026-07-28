dependencies {
    implementation(project(":utils:mongodb-util"))
    implementation(project(":core:flamingock-core-commons"))

    compileOnlyApi("org.mongodb:mongodb-driver-reactivestreams:4.0.0")
}

description = "MongoDB reactive streams driver utilities"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}

configurations.testImplementation {
    extendsFrom(configurations.compileOnly.get())
}
