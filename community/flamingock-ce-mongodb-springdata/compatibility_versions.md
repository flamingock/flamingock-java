# Flamingock Compatibility Matrix

This document provides the definitive compatibility matrix for using Flamingock with Spring Boot and MongoDB Spring Data.

## 🎯 Minimum Compatible Versions

| Component               | **Minimum Version** | **Reason**                                                              |
|-------------------------|---------------------|-------------------------------------------------------------------------|
| **Spring Boot**         | **`2.4.3`**         | ✅ First stable version with MongoDB Driver 4.1 + compatible Spring Data |
| **Spring Data MongoDB** | `3.1.x+`            | Included in Spring Boot 2.4.3, has compatible method signatures         |
| **MongoDB Java Driver** | **`4.0.0+`**        | ✅ Required for `TransactionOptions` class                               |
| **Spring Framework**    | `5.3.x+`            | Bundled with Spring Boot 2.4.3                                          |
| **Java**                | `8+`                | Minimum JVM version                                                     |
| **Gradle**              | `7.0+`              | For Spring Boot 2.4.3 support                                           |

## 🚨 Critical Compatibility Points

### 1. MongoDB Driver 4.0.0+ is MANDATORY
- Flamingock requires `com.mongodb.TransactionOptions` class
- This class was introduced in MongoDB Java Driver 4.0.0
- Spring Boot versions before 2.4.0 use MongoDB Driver 3.x (incompatible)

### 2. Spring Boot 2.4.3 is the Sweet Spot
- ❌ Spring Boot 2.0.0-2.3.x → MongoDB Driver 3.x → Missing `TransactionOptions`
- ❌ Spring Boot 2.4.0-2.4.2 → Unstable Spring Data method signatures
- ✅ **Spring Boot 2.4.3** → MongoDB Driver 4.1 + stable Spring Data 3.1.x

### 3. Spring Data MongoDB Method Compatibility
- Flamingock calls `MongoTemplate.getMongoDatabaseFactory()`
- This method signature is stable in Spring Data MongoDB 3.1.x+
- Earlier versions had different method names/signatures

## 📋 Configuration Examples

### For New Projects
```gradle
plugins {
    java
    id("org.springframework.boot") version "2.4.3" // Minimum
    id("io.spring.dependency-management") version "1.0.10.RELEASE"
}

dependencies {
    // Flamingock dependencies
    implementation(platform("io.flamingock:flamingock-ce-bom:0.0.38-beta"))
    implementation("io.flamingock:flamingock-community")
    implementation("io.flamingock:flamingock-springboot-integration")
    annotationProcessor("io.flamingock:flamingock-processor:0.0.38-beta")
    
    // Spring Boot dependencies (includes MongoDB Driver 4.1)
    implementation("org.springframework.boot:spring-boot-starter-data-mongodb")
}
```

### For Existing Projects
If you're upgrading from an older Spring Boot version:

1. **Upgrade Spring Boot to 2.4.3+**
2. **Add Flamingock dependencies**
3. **Remove any manual MongoDB driver dependencies** (Spring Boot manages them)
4. **Test thoroughly** - method signatures may have changed

## ⚠️ Known Issues

### Spring Boot < 2.4.3
- **Error**: `java.lang.NoClassDefFoundError: com/mongodb/TransactionOptions`
- **Solution**: Upgrade to Spring Boot 2.4.3+

### Spring Boot 2.4.0-2.4.2
- **Error**: `java.lang.NoSuchMethodError: org.springframework.data.mongodb.core.MongoTemplate.getMongoDatabaseFactory()`
- **Solution**: Upgrade to Spring Boot 2.4.3+

### Manual MongoDB Driver Dependencies
- **Issue**: Version conflicts when manually adding MongoDB driver dependencies
- **Solution**: Let Spring Boot manage MongoDB driver versions automatically

## 🚀 Upgrade Path

1. **Check Current Versions**:
   ```gradle
   ./gradlew dependencies --configuration runtimeClasspath | grep -E "(spring-boot|mongodb)"
   ```

2. **Upgrade Spring Boot**:
    - Update `build.gradle.kts` to Spring Boot 2.4.3+
    - Update Gradle wrapper if needed: `./gradlew wrapper --gradle-version 7.6.4`

3. **Add Flamingock**:
    - Add Flamingock BOM and dependencies
    - Add annotation processor

4. **Clean Dependencies**:
    - Remove manual MongoDB driver dependencies
    - Let Spring Boot manage versions automatically

5. **Test Integration**:
    - Run tests: `./gradlew test`
    - Verify Flamingock execution in logs

## 📚 Additional Resources

- [Spring Boot Release Notes](https://github.com/spring-projects/spring-boot/wiki)
- [Spring Data MongoDB Requirements](https://docs.spring.io/spring-data/mongodb/reference/preface.html)
- [MongoDB Java Driver Compatibility](https://www.mongodb.com/docs/drivers/java/sync/current/compatibility/)

## 🧪 Tested Configuration

This compatibility matrix has been verified with:
- ✅ Spring Boot 2.4.3
- ✅ Flamingock 0.0.38-beta
- ✅ MongoDB Java Driver 4.1
- ✅ Spring Data MongoDB 3.1.1
- ✅ Java 8
- ✅ Gradle 7.6.4

Last updated: September 2025