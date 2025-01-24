dependencies {
    implementation(project(":flamingock-core"))
    implementation(project(":local-drivers:mongodb:mongodb-facade"))

    implementation("org.mongodb:mongodb-driver-sync:4.3.3")

    testImplementation(project(":flamingock-core"))
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