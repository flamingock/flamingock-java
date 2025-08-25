dependencies {
    implementation(project(":core:flamingock-core"))
    api(project(":core:flamingock-core-api"))
    api(project(":community:flamingock-ce-couchbase"))
    api(project(":community:flamingock-ce-dynamodb"))
    api(project(":community:flamingock-ce-mongodb-springdata-v3-legacy"))
    api(project(":community:flamingock-ce-mongodb-sync"))
}

description = "${project.name}'s description"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}