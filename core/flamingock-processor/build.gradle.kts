val jacksonVersion = "2.16.0"
val generalUtilVersion: String by extra
dependencies {
    api(project(":core:flamingock-core-commons"))
    api("io.flamingock:flamingock-general-util:${generalUtilVersion}")//todo implementation
    api("org.yaml:snakeyaml:2.2")//todo implementation
    api("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")//todo implementation
}

description = "Annotation processor for generating change metadata and pipeline configurations from @Change annotations and YAML templates"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}
