val jacksonVersion = "2.16.0"

dependencies {
    api(project(":core:flamingock-core-commons"))

    implementation("javax.inject:javax.inject:1")
    implementation("org.javassist:javassist:3.30.2-GA")
    implementation("com.google.code.findbugs:jsr305:3.0.2")
    implementation("org.objenesis:objenesis:3.2")
    implementation("org.yaml:snakeyaml:2.2")

    implementation("org.apache.httpcomponents:httpclient:4.5.14")
    
    implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")


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
