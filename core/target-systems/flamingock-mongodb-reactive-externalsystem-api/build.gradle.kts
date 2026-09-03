val coreApiVersion: String by extra

dependencies {
    api(project(":core:flamingock-core-commons"))

    compileOnly("org.mongodb:mongodb-driver-reactivestreams:4.0.0")
}

description = "MongoDB reactive external system api"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}
