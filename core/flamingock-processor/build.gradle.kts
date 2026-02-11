val jacksonVersion = "2.16.0"
dependencies {
    api(project(":core:flamingock-core-commons"))
    api(project(":utils:general-util"))//todo implementation
    api("org.yaml:snakeyaml:2.2")//todo implementation
    api("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")//todo implementation
}

description = "Annotation processor for generating change metadata and pipeline configurations from @Change annotations and YAML templates"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}
