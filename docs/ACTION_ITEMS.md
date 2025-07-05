# Flamingock Project - Dependency Issues & Action Items

**Document Version**: 1.0  
**Date**: 2025-01-06  
**Priority**: High  
**Assignee**: Development Team Lead  

## Table of Contents

1. [Executive Summary](#executive-summary)
2. [Critical Issues (Fix Immediately)](#-critical-issues-fix-immediately)
3. [Medium Priority Issues](#-medium-priority-issues)
4. [Low Priority Items](#-low-priority-items)
5. [Implementation Checklist](#implementation-checklist)
6. [Validation Steps](#validation-steps)
7. [Risk Assessment](#risk-assessment)
8. [Communication Plan](#communication-plan)

## Related Documents

- **[Architecture Overview](ARCHITECTURE_OVERVIEW.md)** - Complete module architecture and diagrams
- **[Dependency Analysis](DEPENDENCY_ANALYSIS.md)** - Detailed technical dependency analysis

## Executive Summary

This document outlines critical dependency issues discovered during the architecture analysis and provides specific action items to resolve them. These issues should be addressed to ensure consistency, avoid conflicts, and maintain a clean architecture.

## 🔴 Critical Issues (Fix Immediately)

### 1. Jackson Version Inconsistency
**Impact**: Version conflicts, potential runtime ClassNotFound exceptions  
**Risk Level**: HIGH  

**Issue**:
```gradle
// Most modules use:
val jacksonVersion = "2.16.0"

// But flamingock-graalvm uses:
implementation("com.fasterxml.jackson.core:jackson-databind:2.15.2")
```

**Action Required**:
```gradle
// Fix in flamingock-graalvm/build.gradle.kts
val jacksonVersion = "2.16.0"  // Standardize to 2.16.0
dependencies {
    api(project(":core:flamingock-core"))
    implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")  // ← Fix version
    compileOnly("org.graalvm.sdk:graal-sdk:22.3.0")
}
```

**Estimated Effort**: 15 minutes  
**Test Required**: GraalVM native image compilation tests  

### 2. Duplicate Dependency Declaration
**Impact**: Build confusion, potential classpath issues  
**Risk Level**: MEDIUM  

**Issue**:
```gradle
// In flamingock-core-commons/build.gradle.kts
api(project(":utils:general-util"))
implementation(project(":utils:general-util"))  // ← DUPLICATE!
```

**Action Required**:
```gradle
// Fix in flamingock-core-commons/build.gradle.kts
dependencies {
    api(project(":utils:general-util"))
    api(project(":core:flamingock-core-api"))
    // REMOVE: implementation(project(":utils:general-util"))
    api("jakarta.annotation:jakarta.annotation-api:2.1.1")
    
    implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
    implementation("com.fasterxml.jackson.core:jackson-core:$jacksonVersion")
}
```

**Estimated Effort**: 5 minutes  
**Test Required**: Compilation and dependency resolution tests  

### 3. Incorrect AWS SDK Dependency Exposure
**Impact**: Forces specific AWS SDK version on users, conflicts with user's AWS SDK version  
**Risk Level**: HIGH  

**Issue**:
```gradle
// In dynamodb-transactioner/build.gradle.kts
implementation("software.amazon.awssdk:dynamodb-enhanced:2.25.28")  // ← Should be compileOnly
```

**Action Required**:
```gradle
// Fix in dynamodb-transactioner/build.gradle.kts
dependencies {
    implementation(project(":utils:dynamodb-util"))
    api(project(":core:flamingock-core"))
    
    compileOnly("software.amazon.awssdk:dynamodb-enhanced:[2.0.0,3.0.0)")  // ← Use version range + compileOnly
    
    testImplementation("software.amazon.awssdk:url-connection-client:2.24.11")
    testImplementation("com.amazonaws:DynamoDBLocal:1.25.0")
    testImplementation(project(":cloud:flamingock-cloud"))
    testImplementation(project(":utils:test-util"))
}

// Also add to test configuration
configurations.testImplementation {
    extendsFrom(configurations.compileOnly.get())
}
```

**Estimated Effort**: 10 minutes  
**Test Required**: DynamoDB integration tests  

## 🟡 Medium Priority Issues

### 4. Empty Cloud BOM
**Impact**: No dependency management for cloud users  
**Risk Level**: MEDIUM  

**Issue**:
```gradle
// flamingock-cloud-bom/build.gradle.kts is mostly empty
dependencies {
    constraints {
        // TODO add here all the client-facing artefacts that the ce users can use
    }
}
```

**Action Required**:
```gradle
// Complete flamingock-cloud-bom/build.gradle.kts
dependencies {
    constraints {
        // Core modules
        api(project(":cloud:flamingock-cloud"))
        api(project(":core:flamingock-core"))
        api(project(":core:flamingock-core-api"))
        api(project(":core:flamingock-core-commons"))
        api(project(":core:flamingock-processor"))
        api(project(":core:flamingock-graalvm"))
        
        // Platform integrations
        api(project(":platform-plugins:flamingock-springboot-integration"))
        
        // External dependencies
        api("com.fasterxml.jackson.core:jackson-databind:2.16.0")
        api("jakarta.annotation:jakarta.annotation-api:2.1.1")
        api("org.yaml:snakeyaml:2.2")
        api("org.apache.httpcomponents:httpclient:4.5.14")
        api("javax.inject:javax.inject:1")
    }
}
```

**Estimated Effort**: 30 minutes  
**Test Required**: BOM import tests  

### 5. Missing Couchbase Transactioner
**Impact**: Inconsistent pattern compared to other database integrations  
**Risk Level**: LOW  

**Issue**: `flamingock-ce-couchbase` doesn't use a transactioner pattern like MongoDB and DynamoDB

**Action Required**: 
1. Create `couchbase-transactioner` module
2. Update `flamingock-ce-couchbase` to use it
3. Follow the same pattern as other database integrations

**Estimated Effort**: 2-4 hours  
**Test Required**: Couchbase integration tests  

## 🟢 Low Priority Items

### 6. Incomplete SQL Modules
**Impact**: Missing functionality for SQL users  
**Risk Level**: LOW  

**Modules Needing Implementation**:
- `sql-transactioner` (exists but empty)
- `flamingock-sql-template` (exists but empty)

**Action Required**: Plan and implement SQL support when needed by roadmap

### 7. Community Edition BOM
**Impact**: No dependency management for community users  
**Risk Level**: LOW  

**Issue**: `flamingock-ce-bom` should contain constraints for all community modules

**Action Required**: Add dependency constraints similar to cloud BOM

## Implementation Checklist

### Phase 1: Critical Fixes (Do First)
- [ ] **Fix Jackson version in flamingock-graalvm** 
  - File: `core/flamingock-graalvm/build.gradle.kts`
  - Change: Use Jackson 2.16.0
  - Test: Run GraalVM tests

- [ ] **Remove duplicate dependency in flamingock-core-commons**
  - File: `core/flamingock-core-commons/build.gradle.kts`  
  - Change: Remove duplicate general-util implementation dependency
  - Test: Verify compilation

- [ ] **Fix AWS SDK exposure in dynamodb-transactioner**
  - File: `transactioners/dynamodb-transactioner/build.gradle.kts`
  - Change: Use compileOnly with version range
  - Test: Run DynamoDB tests

### Phase 2: Medium Priority (Do Next)
- [ ] **Complete Cloud BOM**
  - File: `cloud/flamingock-cloud-bom/build.gradle.kts`
  - Change: Add all cloud-related dependency constraints
  - Test: Create BOM usage test

- [ ] **Create Couchbase Transactioner** (if needed)
  - Create: `transactioners/couchbase-transactioner/`
  - Update: `community/flamingock-ce-couchbase/build.gradle.kts`
  - Test: Couchbase integration tests

### Phase 3: Future Improvements
- [ ] **Complete SQL Support**
- [ ] **Complete Community BOM**  
- [ ] **Review all version ranges for consistency**

## Validation Steps

After implementing fixes:

1. **Build Verification**:
   ```bash
   ./gradlew clean build
   ```

2. **Dependency Resolution Check**:
   ```bash
   ./gradlew dependencies --configuration compileClasspath
   ```

3. **Test Suite Validation**:
   ```bash
   ./gradlew test
   ```

4. **Integration Test Verification**:
   ```bash
   ./gradlew integrationTest  # if available
   ```

## Risk Assessment

| Issue | Impact | Likelihood | Priority |
|-------|--------|------------|----------|
| Jackson Version Conflict | High | High | Critical |
| AWS SDK Version Lock | High | Medium | Critical |
| Duplicate Dependencies | Medium | Low | Medium |
| Empty Cloud BOM | Medium | Medium | Medium |
| Missing Transactioners | Low | Low | Low |

## Communication Plan

1. **Immediate**: Notify development team of critical issues
2. **Pre-Fix**: Create branch `fix/dependency-issues`
3. **Post-Fix**: Update team on completed fixes
4. **Documentation**: Update architecture docs after changes
5. **Team Training**: Review Internal vs IBU/UBU classifications with team

---

**Owner**: Architecture Team  
**Reviewers**: Senior Developers, DevOps Team  
**Next Review**: After Phase 1 completion