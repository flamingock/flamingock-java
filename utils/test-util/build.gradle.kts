val jacksonVersion = "2.16.0"
dependencies {

    api(project(":utils:general-util"))
    api(project(":core:flamingock-core"))
    api(project(":core:flamingock-core-api"))

    api("javax.inject:javax.inject:1")
    api("org.reflections:reflections:0.10.1")
    api("org.objenesis:objenesis:3.2")
    api("org.yaml:snakeyaml:2.2")

    api("org.apache.httpcomponents:httpclient:4.5.14")
    
    api("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
    

    api("com.github.tomakehurst:wiremock-jre8:2.35.2")

}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}
