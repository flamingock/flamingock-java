dependencies {
    api(project(":core:flamingock-core"))
    compileOnlyApi("javax.inject:javax.inject:1")
    testImplementation(project(":utils:test-util"))
}

description = "Provides a compatibility layer for Mongock-based applications, including drop-in annotations and audit store migration utilities to Flamingock."
