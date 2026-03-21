val jacksonVersion = "2.16.0"
val generalUtilVersion: String by extra

dependencies {
    api(project(":core:flamingock-core-commons"))
    api("io.flamingock:flamingock-general-util:${generalUtilVersion}")//todo implementation

    api("javax.inject:javax.inject:1")
    api("org.javassist:javassist:3.30.2-GA")
    api("com.google.code.findbugs:jsr305:3.0.2")
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
