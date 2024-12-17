dependencies {
    implementation(project(":flamingock-core-api"))
    implementation(project(":flamingock-core-template"))
}

description = "${project.name}'s description"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}