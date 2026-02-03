dependencies {
// Core
    implementation(project(":core:flamingock-core"))
    api(project(":core:flamingock-core-api"))
// target systems
    api(project(":core:target-systems:nontransactional-target-system"))
    api(project(":core:target-systems:couchbase-target-system"))
    api(project(":core:target-systems:dynamodb-target-system"))
    api(project(":core:target-systems:mongodb-external-system-api"))
    api(project(":core:target-systems:mongodb-springdata-target-system"))
    api(project(":core:target-systems:mongodb-sync-target-system"))
    api(project(":core:target-systems:sql-target-system"))
// Community
    api(project(":community:flamingock-auditstore-couchbase"))
    api(project(":community:flamingock-auditstore-dynamodb"))
    api(project(":community:flamingock-auditstore-mongodb-sync"))
    api(project(":community:flamingock-auditstore-sql"))


 // Test
    testAnnotationProcessor(project(":core:flamingock-processor"))
    testImplementation(project(":utils:test-util"))
    testImplementation(project(":core:target-systems:nontransactional-target-system"))
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