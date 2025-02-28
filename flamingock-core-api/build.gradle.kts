val jacksonVersion = "2.16.0"
dependencies {
    implementation(project(":utils"))
    api("jakarta.annotation:jakarta.annotation-api:2.1.1")

    implementation("com.fasterxml.jackson.core:jackson-core:$jacksonVersion")
}

description = "${project.name}'s description"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}