val coreApiVersion: String by extra
dependencies {
    api("io.flamingock:flamingock-core-api:${coreApiVersion}")

    //General
    compileOnlyApi("software.amazon.awssdk:dynamodb:2.25.29")
}

description = "DynamoDB external system api"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}
