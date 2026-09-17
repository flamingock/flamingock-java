dependencies {
// Core
    api(project(":cloud:flamingock-cloud-api"))
    api(project(":core:flamingock-core"))
// target systems
    api(project(":core:target-systems:flamingock-nontransactional-targetsystem"))
    api(project(":core:target-systems:flamingock-couchbase-targetsystem"))
    api(project(":core:target-systems:flamingock-dynamodb-targetsystem"))
    api(project(":core:target-systems:flamingock-mongodb-externalsystem-api"))
    api(project(":core:target-systems:flamingock-mongodb-reactive-externalsystem-api"))
    api(project(":core:target-systems:flamingock-mongodb-reactive-targetsystem"))
    api(project(":core:target-systems:flamingock-mongodb-springdata-targetsystem"))
    api(project(":core:target-systems:flamingock-mongodb-springdata-reactive-targetsystem"))
    api(project(":core:target-systems:flamingock-mongodb-sync-targetsystem"))
    api(project(":core:target-systems:flamingock-sql-targetsystem"))
// Community
    api(project(":community:flamingock-couchbase-auditstore"))
    api(project(":community:flamingock-dynamodb-auditstore"))
    api(project(":community:flamingock-mongodb-reactive-auditstore"))
    api(project(":community:flamingock-mongodb-sync-auditstore"))
    api(project(":community:flamingock-sql-auditstore"))

    implementation("org.apache.httpcomponents:httpclient:4.5.14")

     // Test
    testImplementation("com.github.tomakehurst:wiremock-jre8:2.35.2")
    testAnnotationProcessor(project(":core:flamingock-processor"))
    testImplementation(project(":utils:test-util"))
    testImplementation(project(":core:target-systems:flamingock-nontransactional-targetsystem"))
}


description = "Cloud Edition implementation with advanced governance, observability, and SaaS features"


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
