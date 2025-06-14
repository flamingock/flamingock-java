dependencies {
    implementation(project(":core:flamingock-core-commons"))
}

description = "${project.name}'s description"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}