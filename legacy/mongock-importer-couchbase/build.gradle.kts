dependencies {
    implementation(project(":core:flamingock-core-commons"))
    implementation(project(":utils:couchbase-util"))

    //General
    compileOnly("com.couchbase.client:java-client:3.6.0")
}


description = "A MongoDB migration utility that imports Mongock’s execution history into Flamingock-Community’s audit store for smooth project upgrades."


java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}
