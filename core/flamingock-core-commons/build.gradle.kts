val jacksonVersion = "2.16.0"
val generalUtilVersion: String by extra
dependencies {
    api(project(":core:flamingock-core-api"))
    api("io.flamingock:flamingock-general-util:${generalUtilVersion}")//todo implementation
    api("jakarta.annotation:jakarta.annotation-api:2.1.1")//todo can this be implementation?

    implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
    implementation("com.fasterxml.jackson.core:jackson-core:$jacksonVersion")
}

description = "Shared internal utilities, preview system, and common infrastructure components"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}