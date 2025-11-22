dependencies {
    implementation(project(":core:flamingock-core-commons"))
    implementation(project(":utils:dynamodb-util"))

    compileOnly("software.amazon.awssdk:dynamodb-enhanced:2.25.29")
}


description = "A MongoDB migration utility that imports Mongock’s execution history into Flamingock-Community’s audit store for smooth project upgrades."


java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}
