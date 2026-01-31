dependencies {
    implementation(project(":core:flamingock-core"))
    implementation(project(":utils:general-util"))

    compileOnly("software.amazon.awssdk:dynamodb-enhanced:2.25.29")
}

description = "Amazon DynamoDB utilities and helpers for database operations"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}