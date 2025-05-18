val jacksonVersion = "2.16.0"

dependencies {
    api(project(":core:flamingock-core-api"))
    api(project(":core:flamingock-processor"))
    api(project(":utils:general-util"))
    api("javax.inject:javax.inject:1")
    api("org.reflections:reflections:0.10.1")//TODO remove
    api("org.objenesis:objenesis:3.2")
    api("org.yaml:snakeyaml:2.2")

    api("org.apache.httpcomponents:httpclient:4.5.14")
    
    api("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")


//    testAnnotationProcessor(project(":core:flamingock-processor"))
    testImplementation(project(":utils:test-util"))
}

description = "${project.name}'s description"


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
