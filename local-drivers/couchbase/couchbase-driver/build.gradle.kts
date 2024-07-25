
dependencies {
    api(project(":local-drivers:driver-common"))
    
    implementation("com.couchbase.client:java-client:3.4.4")

    testImplementation("org.testcontainers:couchbase:1.18.3")
    testImplementation("org.testcontainers:junit-jupiter:1.18.3")
}
