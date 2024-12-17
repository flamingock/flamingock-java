dependencies {
    api(project(":local-drivers:driver-common"))

}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}