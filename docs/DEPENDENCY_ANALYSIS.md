# Flamingock Project - Detailed Dependency Analysis

**Document Version**: 1.0  
**Date**: 2025-01-06  
**Audience**: Development Team, Architecture Review  

## Table of Contents

1. [Overview](#overview)
2. [Module-by-Module Dependency Analysis](#module-by-module-dependency-analysis)
   - [Core Framework](#core-framework)
   - [Cloud Edition](#cloud-edition)
   - [Community Edition](#community-edition)
   - [Transactioners](#transactioners)
   - [Templates](#templates)
   - [Platform Integration](#platform-integration)
   - [Utilities](#utilities)
3. [External Dependency Summary](#external-dependency-summary)
4. [Dependency Pattern Analysis](#dependency-pattern-analysis)

## Related Documents

- **[Architecture Overview](ARCHITECTURE_OVERVIEW.md)** - Visual architecture diagrams and module relationships
- **[Action Items](ACTION_ITEMS.md)** - Specific fixes needed for dependency issues

## Overview

This document provides a comprehensive analysis of all module dependencies in the Flamingock project, including external libraries, version ranges, and dependency patterns. It serves as a technical reference for understanding the project's dependency landscape.

### Module Type Classifications

- **IBU (Import By User)**: Libraries designed for direct import by end users
- **UBU (Used By User)**: Modules providing APIs that users access but don't import directly  
- **Internal**: Implementation modules not exposed to end users
- **BOM**: Bill of Materials for dependency management

## Module-by-Module Dependency Analysis

### Core Framework

#### `flamingock-core-api`
```gradle
dependencies {
    implementation(project(":utils:general-util"))
    api("jakarta.annotation:jakarta.annotation-api:2.1.1")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.16.0")
    implementation("com.fasterxml.jackson.core:jackson-core:2.16.0")
}
```
- **Purpose**: Core annotations and APIs for end users
- **Java Version**: 8+
- **External Dependencies**: Jakarta Annotations (API), Jackson (internal)

#### `flamingock-core-commons`
```gradle
dependencies {
    api(project(":utils:general-util"))
    api(project(":core:flamingock-core-api"))
    implementation(project(":utils:general-util"))  // ⚠️ DUPLICATE
    api("jakarta.annotation:jakarta.annotation-api:2.1.1")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.16.0")
    implementation("com.fasterxml.jackson.core:jackson-core:2.16.0")
}
```
- **Purpose**: Common internal components and utilities
- **Java Version**: 8+
- **Issues**: Duplicate general-util dependency

#### `flamingock-core`
```gradle
dependencies {
    api(project(":core:flamingock-core-commons"))
    api(project(":core:flamingock-processor"))
    api(project(":core:flamingock-importer"))
    api(project(":utils:general-util"))
    
    api("javax.inject:javax.inject:1")
    api("org.reflections:reflections:0.10.1")
    api("org.objenesis:objenesis:3.2")
    api("org.yaml:snakeyaml:2.2")
    api("org.apache.httpcomponents:httpclient:4.5.14")
    api("com.fasterxml.jackson.core:jackson-databind:2.16.0")
    
    testImplementation(project(":utils:test-util"))
}
```
- **Purpose**: Main framework implementation
- **Java Version**: 8+
- **Notes**: Exposes many dependencies as API (intentional for user convenience)

#### `flamingock-processor`
```gradle
dependencies {
    api(project(":core:flamingock-core-commons"))
    api(project(":utils:general-util"))
    api("org.yaml:snakeyaml:2.2")
    api("com.fasterxml.jackson.core:jackson-databind:2.16.0")
}
```
- **Purpose**: Annotation processor for compile-time code generation
- **Java Version**: 8+
- **Notes**: Minimal dependencies, appropriate for annotation processor

#### `flamingock-graalvm`
```gradle
dependencies {
    api(project(":core:flamingock-core"))
    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.2")  // ⚠️ VERSION MISMATCH
    compileOnly("org.graalvm.sdk:graal-sdk:22.3.0")
}
```
- **Purpose**: GraalVM native image support
- **Java Version**: 17+
- **Issues**: Jackson version inconsistency (2.15.2 vs 2.16.0)

#### `flamingock-importer`
```gradle
dependencies {
    implementation(project(":core:flamingock-core-commons"))
    
    compileOnly("org.mongodb:mongodb-driver-sync:4.0.0")
    compileOnly("software.amazon.awssdk:dynamodb-enhanced:[2.0.0,3.0.0)")
    compileOnly("com.couchbase.client:java-client:3.4.3")
    
    // Test dependencies...
}
```
- **Purpose**: Migration tools from other frameworks
- **Java Version**: 8+
- **Notes**: Good use of compileOnly for database drivers

### Cloud Edition

#### `flamingock-cloud`
```gradle
dependencies {
    implementation(project(":core:flamingock-core"))
    
    testAnnotationProcessor(project(":core:flamingock-processor"))
    testImplementation(project(":utils:test-util"))
}
```
- **Purpose**: Cloud/SaaS implementation
- **Java Version**: 8+
- **Notes**: Simple dependency structure

#### `flamingock-cloud-bom`
```gradle
dependencies {
    constraints {
        // TODO: Add constraints for cloud dependencies
    }
}
```
- **Purpose**: Dependency management for cloud edition
- **Issues**: Empty BOM - missing dependency constraints

### Community Edition

#### `flamingock-ce-commons`
```gradle
dependencies {
    implementation(project(":core:flamingock-core"))
    api(project(":core:flamingock-core-api"))
}
```
- **Purpose**: Base for all community edition modules
- **Java Version**: 8+
- **Notes**: Clean dependency structure

#### `flamingock-ce-mongodb-sync`
```gradle
dependencies {
    implementation(project(":utils:mongodb-util"))
    implementation(project(":core:flamingock-core"))
    api(project(":transactioners:mongodb-sync-transactioner"))
    api(project(":community:flamingock-ce-commons"))
    
    compileOnly("org.mongodb:mongodb-driver-sync:4.0.0")
    
    // Test dependencies...
}
```
- **Purpose**: MongoDB synchronous driver integration
- **Java Version**: 8+
- **Notes**: Proper use of compileOnly for MongoDB driver

#### `flamingock-ce-dynamodb`
```gradle
dependencies {
    implementation(project(":utils:dynamodb-util"))
    implementation(project(":core:flamingock-core"))
    api(project(":community:flamingock-ce-commons"))
    api(project(":transactioners:dynamodb-transactioner"))
    
    compileOnly("software.amazon.awssdk:dynamodb-enhanced:[2.0.0,3.0.0)")
    
    // Test dependencies...
}
```
- **Purpose**: DynamoDB integration
- **Java Version**: 8+
- **Notes**: Good pattern for database integration

#### `flamingock-ce-couchbase`
```gradle
dependencies {
    implementation(project(":core:flamingock-core"))
    api(project(":community:flamingock-ce-commons"))
    
    compileOnly("com.couchbase.client:java-client:3.4.3")
    
    // Test dependencies...
}
```
- **Purpose**: Couchbase integration
- **Java Version**: 8+
- **Notes**: No transactioner - might need one for consistency

#### `flamingock-ce-mongodb-springdata`
```gradle
dependencies {
    implementation(project(":utils:mongodb-util"))
    implementation(project(":core:flamingock-core"))
    api(project(":community:flamingock-ce-mongodb-sync"))
    
    compileOnly("org.mongodb:mongodb-driver-sync:[4.8.0, 5.6.0)")
    compileOnly("org.springframework.data:spring-data-mongodb:[4.0.0, 5.0.0)")
    compileOnly("org.springframework.boot:spring-boot-autoconfigure:[3.0.0, 4.0.0)")
    
    // Test dependencies...
}
```
- **Purpose**: Spring Data MongoDB v4.x integration
- **Java Version**: 17+
- **Notes**: Narrower version ranges for compatibility

### Transactioners

#### `mongodb-sync-transactioner`
```gradle
dependencies {
    api(project(":core:flamingock-core"))
    implementation(project(":utils:mongodb-util"))
    
    compileOnly("org.mongodb:mongodb-driver-sync:4.0.0")
    
    // Test dependencies...
}
```
- **Purpose**: MongoDB transaction management
- **Java Version**: 8+
- **Notes**: Proper API exposure of core

#### `dynamodb-transactioner`
```gradle
dependencies {
    implementation(project(":utils:dynamodb-util"))
    api(project(":core:flamingock-core"))
    
    implementation("software.amazon.awssdk:dynamodb-enhanced:2.25.28")  // ⚠️ SHOULD BE compileOnly
    
    // Test dependencies...
}
```
- **Purpose**: DynamoDB transaction management
- **Java Version**: 8+
- **Issues**: Should use compileOnly for AWS SDK like other database modules

#### `sql-transactioner`
```gradle
dependencies {
    // No dependencies defined yet
}
```
- **Purpose**: SQL transaction management
- **Status**: Not yet implemented

### Templates

#### `flamingock-mongodb-sync-template`
```gradle
dependencies {
    implementation(project(":core:flamingock-core-commons"))
    compileOnly("org.mongodb:mongodb-driver-sync:4.0.0")
    
    // Test dependencies...
}
```
- **Purpose**: MongoDB template support
- **Java Version**: 8+
- **Notes**: Minimal dependencies, appropriate for template

#### `flamingock-sql-template`
```gradle
dependencies {
    // Module exists but no dependencies defined yet
}
```
- **Purpose**: SQL template support
- **Status**: Not yet implemented

### Platform Integration

#### `flamingock-springboot-integration`
```gradle
dependencies {
    api(project(":core:flamingock-core"))
    implementation(project(":core:flamingock-core-commons"))
    
    compileOnly("org.springframework:spring-context:[6.0.0,7.0.0)")
    compileOnly("org.springframework.boot:spring-boot:[3.0.0,4.0.0)")
    compileOnly("org.springframework.boot:spring-boot-autoconfigure:[3.0.0,4.0.0)")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor:[3.0.0,4.0.0)")
    
    // Test dependencies...
}
```
- **Purpose**: Spring Boot v3.x integration
- **Java Version**: 17+
- **Notes**: Proper use of compileOnly for Spring dependencies

### Utilities

#### `general-util`
```gradle
dependencies {
    implementation("org.reflections:reflections:0.10.1")
    api("org.yaml:snakeyaml:2.2")
    api("org.apache.httpcomponents:httpclient:4.5.14")
    api("com.fasterxml.jackson.core:jackson-databind:2.16.0")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jdk8:2.16.0")
}
```
- **Purpose**: Shared utilities across all modules
- **Java Version**: 8+
- **Notes**: Good API exposure strategy

#### `mongodb-util`
```gradle
dependencies {
    // Dependencies extend from general-util
}
```
- **Purpose**: MongoDB-specific utilities
- **Notes**: Inherits from general-util

#### `dynamodb-util`
```gradle
dependencies {
    // Dependencies extend from general-util
}
```
- **Purpose**: DynamoDB-specific utilities
- **Notes**: Inherits from general-util

#### `test-util`
```gradle
dependencies {
    api(project(":utils:general-util"))
    api(project(":core:flamingock-core"))
    api(project(":core:flamingock-core-commons"))
    
    api("javax.inject:javax.inject:1")
    api("org.reflections:reflections:0.10.1")
    api("org.objenesis:objenesis:3.2")
    api("org.yaml:snakeyaml:2.2")
    api("org.apache.httpcomponents:httpclient:4.5.14")
    api("com.fasterxml.jackson.core:jackson-databind:2.16.0")
    api("com.github.tomakehurst:wiremock-jre8:2.35.2")
}
```
- **Purpose**: Testing utilities and helpers
- **Java Version**: 8+
- **Notes**: Includes WireMock for testing

## External Dependency Summary

### Jackson (JSON Processing)
- **Versions Used**: 2.16.0 (most), 2.15.2 (graalvm - inconsistent)
- **Modules**: All core modules, utils
- **Exposure**: Mixed (API in utils, implementation in others)

### Database Drivers
- **MongoDB**: `4.0.0` - Wide compatibility range
- **DynamoDB**: `[2.0.0,3.0.0)` - AWS SDK compatibility
- **Couchbase**: `[3.0.0,4.0.0)` - Major version compatibility

### Spring Framework
- **Spring Core**: `[6.0.0,7.0.0)` - Spring 6 support
- **Spring Boot**: `[3.0.0,4.0.0)` - Spring Boot 3 support
- **Spring Data**: `[4.0.0,5.0.0)` - Spring Data 4 support

### Other Key Dependencies
- **Jakarta Annotations**: `2.1.1` - Standard annotations
- **SnakeYAML**: `2.2` - YAML processing
- **HTTP Client**: `4.5.14` - Apache HTTP Client
- **Reflections**: `0.10.1` - Runtime reflection utilities

## Dependency Pattern Analysis

### Good Patterns ✅
1. **Database drivers as `compileOnly`**: Allows users to choose versions
2. **API exposure via `api` dependencies**: Proper transitive dependency management
3. **Version ranges for compatibility**: Flexible integration
4. **Separation of concerns**: Clear module boundaries
5. **Internal module encapsulation**: Implementation details properly hidden from users

### Issues Identified ⚠️
1. **Jackson version inconsistency**: Multiple versions in use
2. **Duplicate dependencies**: Same dependency declared multiple times
3. **Incorrect exposure**: Some implementation details exposed as API
4. **Missing BOM content**: Empty dependency management

---

**Related Documents**: 
- [Architecture Overview](ARCHITECTURE_OVERVIEW.md)
- [Action Items](ACTION_ITEMS.md)