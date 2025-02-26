dependencies {
    api(project(":flamingock-core"))

    implementation("org.mongodb:mongodb-driver-sync:4.3.3")
    implementation(project(":commons:mongodb-facade"))

    testImplementation("org.testcontainers:mongodb:1.18.3")
    testImplementation("org.testcontainers:junit-jupiter:1.18.3")

    testImplementation(project(":utils-test"))
}

description = "${project.name}'s description"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}