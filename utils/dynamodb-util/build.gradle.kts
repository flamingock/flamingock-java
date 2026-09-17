dependencies {
    implementation(project(":core:flamingock-core"))

    compileOnlyApi("software.amazon.awssdk:dynamodb-enhanced:2.25.29")
}

description = "Amazon DynamoDB utilities and helpers for database operations"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}
