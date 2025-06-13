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
    compileOnly("org.mongodb:mongodb-driver-sync:[3.7.0, 6.0.0)")
}



java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}
