plugins {
    java
    id("org.springframework.boot") version "2.7.12"
    id("io.spring.dependency-management") version "1.1.0"
}

dependencies {

    implementation("com.mysql:mysql-connector-j:8.2.0")
    implementation(project(":templates:sql-springboot-template"))
    implementation(project(":flamingock-springboot-v2-runner"))

    implementation("org.slf4j:slf4j-simple:2.0.6")

    //TODO remove once cloud is available
    implementation(project(":local-drivers:mongodb:mongodb-springdata-v3-driver"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-mongodb")
}

