plugins {
    id("java")
}

group = "io.flamingock"
version = "0.0.31-beta"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":core:flamingock-core"))
    implementation(project(":core:flamingock-core-api"))
    implementation("org.mongodb:mongodb-driver-sync:4.3.3")
}



java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}
