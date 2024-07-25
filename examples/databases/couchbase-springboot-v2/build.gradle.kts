plugins {
    java
    id("org.springframework.boot") version "2.7.12"
    id("io.spring.dependency-management") version "1.1.0"
}


dependencies {
    implementation(project(":flamingock-springboot-v2-runner"))
    implementation(project(":local-drivers:couchbase:couchbase-springboot-v2-driver"))
    
    implementation("org.slf4j:slf4j-simple:2.0.6")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.data:spring-data-couchbase:4.4.8")
    implementation("com.couchbase.client:java-client:3.4.4")

    testImplementation("org.testcontainers:couchbase:1.18.3")
    testImplementation("org.testcontainers:junit-jupiter:1.18.3")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

