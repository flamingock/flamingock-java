dependencies {
    implementation(project(":core:flamingock-core"))
    api(project(":core:flamingock-core-api"))
}

description = "${project.name}'s description"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}