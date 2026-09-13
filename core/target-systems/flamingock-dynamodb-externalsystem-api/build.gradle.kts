dependencies {
    api(project(":core:flamingock-core-commons"))

    //General
    compileOnlyApi("software.amazon.awssdk:dynamodb:2.25.29")
}

description = "DynamoDB external system api"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}
