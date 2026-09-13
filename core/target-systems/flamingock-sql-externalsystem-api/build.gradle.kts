val sqlVersion: String by extra
dependencies {
    api(project(":core:flamingock-core-commons"))
    implementation("io.flamingock:flamingock-sql-util:${sqlVersion}")

}

description = "DynamoDB external system api"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}
