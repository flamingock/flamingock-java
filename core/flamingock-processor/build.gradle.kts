val jacksonVersion = "2.16.0"
dependencies {
    api(project(":core:flamingock-core-commons"))
    api(project(":utils:general-util"))
    api("org.yaml:snakeyaml:2.2")
    api("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
}

description = "Annotation processor for generating change metadata and pipeline configurations from @Change annotations and YAML templates"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}
