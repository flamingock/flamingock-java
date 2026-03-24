val coreApiVersion: String by extra
dependencies {
// Core
    implementation(project(":core:flamingock-core"))
    api("io.flamingock:flamingock-core-api:${coreApiVersion}")
// target systems
    api(project(":core:target-systems:flamingock-nontransactional-targetsystem"))
    api(project(":core:target-systems:flamingock-couchbase-targetsystem"))
    api(project(":core:target-systems:flamingock-dynamodb-targetsystem"))
    api(project(":core:target-systems:flamingock-mongodb-externalsystem-api"))
    api(project(":core:target-systems:flamingock-mongodb-springdata-targetsystem"))
    api(project(":core:target-systems:flamingock-mongodb-sync-targetsystem"))
    api(project(":core:target-systems:flamingock-sql-targetsystem"))
// Community
    api(project(":community:flamingock-couchbase-auditstore"))
    api(project(":community:flamingock-dynamodb-auditstore"))
    api(project(":community:flamingock-mongodb-sync-auditstore"))
    api(project(":community:flamingock-sql-auditstore"))


 // Test
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