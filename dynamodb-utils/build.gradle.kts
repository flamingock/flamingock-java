plugins {
    id("java")
}


val jacksonVersion = "2.16.0"
dependencies {

    api("software.amazon.awssdk:dynamodb-enhanced:2.25.28")
    

}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}