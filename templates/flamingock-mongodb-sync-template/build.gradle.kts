dependencies {
    implementation(project(":core:flamingock-core-commons"))
    compileOnly("org.mongodb:mongodb-driver-sync:[3.7.0,6.0.0)")
}

description = "${project.name}'s description"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}