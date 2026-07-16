dependencies {
    implementation(project(":utils:mongodb-util"))

    compileOnly("org.mongodb:mongodb-driver-sync:4.0.0")
}

description = "MongoDB synchronous driver utilities"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}

configurations.testImplementation {
    extendsFrom(configurations.compileOnly.get())
}
