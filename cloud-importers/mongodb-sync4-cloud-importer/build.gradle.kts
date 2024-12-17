val jacksonVersion = "2.16.0"
dependencies {
    implementation(project(":utils"))
    implementation(project(":flamingock-core-api"))

    implementation("org.mongodb:mongodb-driver-sync:4.3.3")
    implementation(project(":flamingock-core"))

    api("org.apache.httpcomponents:httpclient:4.5.14")
    
    api("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
    

    testImplementation(project(":utils-test"))
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}
