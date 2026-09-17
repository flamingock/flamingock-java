val coreApiVersion: String by extra
val sqlVersion: String by extra
dependencies {
    api(project(":core:flamingock-core-commons"))

    //General
    compileOnly("software.amazon.awssdk:dynamodb-enhanced:2.25.29")
}

description = "DynamoDB external system api"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}
