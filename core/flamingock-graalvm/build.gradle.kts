val jacksonVersion = "2.15.2"
dependencies {
    //this way the user doesn't need to import the core
    api(project(":core:flamingock-core"))

    implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")

}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}
