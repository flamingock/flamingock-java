plugins {
    id("java")
}

group = "io.flamingock"
version = "0.0.31-beta"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":flamingock-core"))
    implementation(project(":flamingock-core-api"))
    implementation("org.mongodb:mongodb-driver-sync:4.3.3")
}



java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}
