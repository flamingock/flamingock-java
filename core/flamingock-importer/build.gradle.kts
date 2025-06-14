plugins {
    id("java")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":core:flamingock-core-commons"))

    compileOnly("org.mongodb:mongodb-driver-sync:[3.7.0, 6.0.0)")
    compileOnly("software.amazon.awssdk:dynamodb-enhanced:[2.0.0,3.0.0)")
    compileOnly("com.couchbase.client:java-client:[3.0.0,4.0.0)")
}



java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}

configurations.testImplementation {
    extendsFrom(configurations.compileOnly.get())
}