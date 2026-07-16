val coreApiVersion: String by extra

dependencies {
    implementation("io.flamingock:flamingock-core-api:${coreApiVersion}")

    compileOnly("org.mongodb:mongodb-driver-reactivestreams:4.0.0")
}

description = "MongoDB reactive external system api"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}
