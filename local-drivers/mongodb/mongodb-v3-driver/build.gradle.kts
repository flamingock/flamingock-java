
dependencies {
    api(project(":local-drivers:mongodb:mongodb-facade"))

    implementation("org.mongodb:mongo-java-driver:3.12.8")

    testImplementation(project(":flamingock-core"))
    testImplementation("org.testcontainers:mongodb:1.18.3")
    testImplementation("org.testcontainers:junit-jupiter:1.18.3")

}
