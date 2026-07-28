val coreApiVersion: String by extra
val sqlVersion: String by extra
dependencies {
    api("io.flamingock:flamingock-core-api:${coreApiVersion}")
    implementation("io.flamingock:flamingock-sql-util:${sqlVersion}")

}

description = "DynamoDB external system api"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}
