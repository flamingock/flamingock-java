val jacksonVersion = "2.15.2"
dependencies {
    //this way the user doesn't need to import the core
    api(project(":core:flamingock-core"))

    implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")

    // GraalVM SDK for native image support
    compileOnly("org.graalvm.sdk:graal-sdk:22.3.0")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}
