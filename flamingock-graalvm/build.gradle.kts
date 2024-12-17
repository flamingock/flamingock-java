val jacksonVersion = "2.15.2"
dependencies {
    implementation(project(":flamingock-core-api"))

    implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")

}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}
