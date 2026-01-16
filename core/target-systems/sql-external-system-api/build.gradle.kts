dependencies {
    implementation(project(":core:flamingock-core-api"))
    implementation(project(":utils:sql-util"))

    //General
    compileOnly("software.amazon.awssdk:dynamodb-enhanced:2.25.29")
}

description = "DynamoDB external system api"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}