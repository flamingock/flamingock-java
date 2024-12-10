# Migration Guide: From Mongock to Flamingock with Spring Boot

This guide walks you through migrating your Spring Boot application from Mongock to Flamingock, the evolution of Mongock. Follow the steps below to update your dependencies, code references, and configurations.

___
### 1. Replace Mongock Dependencies with Flamingock Dependencies

Update your build configuration to replace Mongock dependencies with Flamingock dependencies.

**Gradle:**
```kotlin
implementation("io.flamingock:flamingock-springboot-v2-runner:$flamingockLatestVersion")
implementation("io.flamingock:mongodb-springdata-v3-driver:$flamingockLatestVersion")
```

**Maven:**
```xml
<dependencies>
   <dependency>
      <groupId>io.flamingock</groupId>
      <artifactId>flamingock-springboot-v2-runner</artifactId>
      <version>${flamingock.latestVersion}</version>
   </dependency>
   <dependency>
      <groupId>io.flamingock</groupId>
      <artifactId>mongodb-springdata-v3-driver</artifactId>
      <version>${flamingock.latestVersion}</version>
   </dependency>
</dependencies>
```

### 2. Update Code References

Replace Mongock-specific references in your code with Flamingock equivalents.

For example: `io.mongock.runner.springboot.EnableMongock` with `io.flamingock.springboot.v2.context.EnableFlamingock.`

> Note: Legacy Mongock annotations will remain supported in Flamingock indefinitely, although they are marked as deprecated.

### 3. Update Configuration File
Update your application's configuration file to match Flamingock's structure.

#### Legacy Mongock Configuration:
```yaml
mongock:
  migration-scan-package:
    - io.flamingock.examples.mongodb.springboot.springdata.mongock
  transactional: true
```

#### Flamingock Equivalent Configuration 
```yaml
flamingock:
  stages:
    - name: mongodb-migration
      code-packages:
        - io.flamingock.examples.mongodb.springboot.springdata.changes
  transactionEnabled: true
```

### 4. Add a New Package (Optional)
If you want to organize new changeUnits separately, add a new package for them. Both legacy Mongock changeUnits and Flamingock changeUnits can coexist in the same package.

#### Example Configuration:
```yaml
flamingock:
   stages:
      - name: mongodb-migration
        code-packages:
           - io.flamingock.examples.mongodb.springboot.springdata.mongock
           - io.flamingock.examples.mongodb.springboot.springdata.changes
   transactionEnabled: true
```

### 5. Specify the Location of Legacy ChangeLogs
Specify where the legacy Mongock changeLogs are located using the property `legacy-mongock-changelog-source`. This is where Flamingock will retrieve the legacy changeLogs to migrate them into the new structure.
#### Updated Configuration:
```yaml
flamingock:
   stages:
      - name: mongodb-migration
        code-packages:
           - io.flamingock.examples.mongodb.springboot.springdata.mongock
           - io.flamingock.examples.mongodb.springboot.springdata.changes
   transactionEnabled: true
   legacy-mongock-changelog-source: mongockChangeLog

```