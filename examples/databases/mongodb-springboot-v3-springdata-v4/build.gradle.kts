plugins {
    java
    id("org.springframework.boot") version "3.1.3"
    id("io.spring.dependency-management") version "1.1.3"
}

dependencies {
    implementation(project(":flamingock-springboot-v3-runner"))
    implementation(project(":local-drivers:mongodb:mongodb-springdata-v4-driver"))
    
    implementation("org.slf4j:slf4j-simple:2.0.7")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-mongodb")

    testImplementation("org.testcontainers:mongodb:1.18.3")

    testImplementation("org.testcontainers:junit-jupiter:1.18.3")

    testImplementation("org.springframework.boot:spring-boot-starter-test")

    configurations {
        all {
            exclude("org.springframework.boot", "spring-boot-starter-logging")
        }
    }

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(17))
        }
    }

    tasks.withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }
}

