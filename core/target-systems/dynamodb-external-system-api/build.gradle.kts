val coreApiVersion: String by extra
dependencies {
    implementation("io.flamingock:flamingock-core-api:${coreApiVersion}")

    //General
    compileOnly("software.amazon.awssdk:dynamodb-enhanced:2.25.29")
}

description = "DynamoDB external system api"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}