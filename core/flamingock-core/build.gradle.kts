val jacksonVersion = "2.16.0"

dependencies {
    api(project(":core:flamingock-core-commons"))
    api(project(":core:importer:flamingock-importer"))
    api(project(":utils:general-util"))

    api("javax.inject:javax.inject:1")
    api("org.reflections:reflections:0.10.1")//TODO remove
    api("org.objenesis:objenesis:3.2")
    api("org.yaml:snakeyaml:2.2")

    api("org.apache.httpcomponents:httpclient:4.5.14")
    
    api("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")


    testImplementation(project(":utils:test-util"))
}

description = "Core engine and orchestration logic for executing versioned changes across distributed systems"


tasks.withType<JavaCompile>().configureEach {
    if (name.contains("Test", ignoreCase = true)) {
        options.compilerArgs.addAll(listOf(
            "-Asources=${projectDir}/src/test/java",
            "-Aresources=${projectDir}/src/test/resources"
        ))
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}
