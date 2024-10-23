
dependencies {
    api(project(":local-drivers:mongodb:mongodb-facade"))

    implementation("org.mongodb:mongo-java-driver:3.12.8")
    implementation("org.mongodb:bson:3.12.8")

    testImplementation(project(":flamingock-core"))
    testImplementation("org.testcontainers:mongodb:1.18.3")
    testImplementation("org.testcontainers:junit-jupiter:1.18.3")

//    Mongock
    testImplementation("io.mongock:mongock-standalone:5.5.0")
    testImplementation("io.mongock:mongodb-sync-v4-driver:5.5.0")


//    <!-- MONGOCK DRIVER -->
//
//
//    <!-- MONGODB DRIVER -->
//    <dependency>
//    <groupId>org.mongodb</groupId>
//    <artifactId>mongodb-driver-sync</artifactId>
//    <version>4.3.3</version>
//    </dependency>
//    <dependency>
//    <groupId>org.mongodb</groupId>
//    <artifactId>bson</artifactId>
//    <version>4.3.3</version>
//    </dependency>
//    <dependency>
//    <groupId>org.mongodb</groupId>
//    <artifactId>mongodb-driver-core</artifactId>
//    <version>4.3.3</version>
//    </dependency>
}
