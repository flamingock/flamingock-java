# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What is Flamingock?

Flamingock is a **"Change-as-Code" platform** that versions and orchestrates any state change that must evolve alongside your application:

| Aspect                   | Description                                                                                                                    |
|--------------------------|--------------------------------------------------------------------------------------------------------------------------------|
| **Scope**                | Databases (SQL/NoSQL), message queues, S3 buckets, feature-flags, REST APIs, etc.                                              |
| **Model**                | Each change is defined as a **ChangeUnit**; executed deterministically, audited and can be reverted.                           |
| **Centralized auditing** | Records who, when and with what result each change was applied; avoids duplicates and facilitates regulatory compliance.       |
| **Rollback & Undo**      | Compensation logic per ChangeUnit to undo or "undo" deployments.                                                               |
| **Automation**           | Runs on app startup or on-demand via CLI/UI, with distributed locking and transactional consistency when the system allows it. |
| **Editions**             | **Community** (OSS) · **Cloud** (managed SaaS, dashboard, RBAC) · **Self-managed** (Cloud in your environment).                |

## What Flamingock is NOT

| Is not                                         | Because…                                                                                                           |
|------------------------------------------------|--------------------------------------------------------------------------------------------------------------------|
| **Infrastructure-as-Code** (Terraform, Pulumi) | Flamingock acts *after* infrastructure exists; focuses on **functional state**, not creating machines or networks. |
| **Just an SQL migrator**                       | Extends the same model to Kafka, S3, Vault, etc.                                                                   |
| **A generic batch job engine**                 | Each ChangeUnit must conclude (or fail) quickly; long processes are modeled through internal *batching*.           |
| **A CI/CD replacement**                        | Integrates with your pipeline, but doesn't compile, test or deploy artifacts. Its focus is **state evolution**.    |

## Key Features

1. **Universal Change-as-Code**  
   - ChangeUnits in Java/Kotlin/Groovy **or** YAML/JSON (low-code templates).  
   - Declarative order, version control, PR review.

2. **Transactional audit store**  
   - *Community* → your database.  
   - *Cloud* → managed backend with dashboards, metrics and RBAC.

3. **Safe rollback**  
   - Explicit compensations or native transactions.  
   - *Undo* command by range, date or tag.

4. **Reusable templates**  
   - SQL, Kafka, Redis, Twilio, etc.  
   - Less boilerplate and proven centralized logic.

5. **Distributed locking**  
   - Prevents duplicate executions in parallel deployments.  
   - Implementations for MongoDB, Redis, DynamoDB…

6. **Batching for massive loads**  
   - Processes large volumes in idempotent fragments.  
   - Automatically resumes after failures.

7. **First-class integrations**  
   - Spring Boot, Micronaut, Quarkus, CLI, REST API.  
   - Dependency injection and access to application context.

8. **Cloud edition with added value**  
   - Real-time dashboard, alerts, RBAC and multi-environment.  
   - No infrastructure operations or audit store backups.

## Build System & Commands

### Core Development Commands
- **Build entire project**: `./gradlew build`
- **Clean build**: `./gradlew clean build`
- **Run tests**: `./gradlew test`
- **Run checks (includes tests)**: `./gradlew check`
- **Build specific module**: `./gradlew :module-name:build`
- **Test specific module**: `./gradlew :module-name:test`

### Module-Specific Commands
- **Build core modules**: `./gradlew core:build`
- **Build cloud modules**: `./gradlew cloud:build`
- **Build community modules**: `./gradlew community:build`

### Release Commands
- **Release specific bundle**: `./gradlew -PreleaseBundle=core jreleaserDeploy`
- **Release specific module**: `./gradlew -Pmodule=flamingock-core jreleaserDeploy`

## Architecture & Module Structure

### Core Framework (`/core/`)
- **flamingock-core**: Main execution engine and framework
- **flamingock-core-api**: Public API with annotations (@ChangeUnit, @Execution, @RollbackExecution)
- **flamingock-core-commons**: Shared utilities and common components
- **flamingock-processor**: Annotation processor for compile-time validation
- **flamingock-graalvm**: GraalVM native compilation support

### Cloud Infrastructure (`/cloud/`)
- **flamingock-cloud**: Cloud-native implementation with remote services
- Features HTTP-based audit writing, authentication, distributed locking

### Community Edition (`/community/`)
- **flamingock-ce-commons**: Community edition base functionality
- Database drivers: MongoDB (sync/spring-data), Couchbase, DynamoDB
- **flamingock-importer**: Migration tooling from Mongock

### Platform Integration (`/platform-plugins/`)
- **flamingock-springboot-integration**: Spring Boot auto-configuration
- **flamingock-springboot-integration-v2-legacy**: Legacy Spring Boot 2.x support

### Transaction Management (`/transactioners/`)
- **sql-transactioner**: SQL database transaction handling
- **mongodb-sync-transactioner**: MongoDB transaction management
- **dynamodb-transactioner**: DynamoDB transaction support

### Templates System (`/templates/`)
- **flamingock-mongodb-sync-template**: MongoDB template for no-code migrations
- **flamingock-sql-template**: SQL template for database schema changes
- Supports YAML-based pipeline definitions

### Utilities (`/utils/`)
- **general-util**: Common utilities (JSON, reflection, time services)
- **test-util**: Testing utilities and helpers
- Database-specific utilities for MongoDB and DynamoDB

## Key Patterns & Concepts

### ChangeUnit Model
- Each change is a **ChangeUnit** with @ChangeUnit annotation
- @Execution for forward operations, @RollbackExecution for compensation logic
- Deterministic execution with comprehensive auditing

### Driver Pattern
- Abstract LocalDriver and CloudDriver interfaces
- Database-specific implementations for pluggable storage backends

### Pipeline-Based Execution
- YAML-based pipeline definitions with staged execution
- Supports parallel and sequential execution streams
- Built-in rollback capabilities for failed executions

### Template System
- AbstractChangeTemplate for no-code migrations
- YAML-driven configuration supporting multiple database operations
- Reusable templates for SQL, Kafka, Redis, etc.

## Important File Locations

### Entry Points
- Cloud: `cloud/flamingock-cloud/src/main/java/io/flamingock/cloud/Flamingock.java`
- Community: `community/flamingock-ce-commons/src/main/java/io/flamingock/community/Flamingock.java`

### Core Annotations
- `core/flamingock-core-api/src/main/java/io/flamingock/api/annotations/ChangeUnit.java`

### Configuration
- Spring Boot integration: `@EnableFlamingock` annotation
- Pipeline configuration through YAML files

## Development Guidelines

### Java Version
- Target: Java 8 compatibility
- Build toolchain uses Java 8 language version
- Testing framework: JUnit 5 with Mockito

### Dependencies
- Uses Kotlin stdlib for build scripts
- SLF4J for logging (version 2.0.6)
- Spring integration where applicable

### Testing
- All modules use JUnit 5 (`jupiter-api`, `jupiter-engine`, `jupiter-params`)
- Mockito for mocking (`mockito-core`, `mockito-junit-jupiter`, `mockito-inline`)
- Test logging includes PASSED, SKIPPED, FAILED, and STANDARD_OUT events

### Module Types
- **Library modules**: Standard Java libraries with sources/javadoc JARs
- **BOM modules**: Bill of Materials using `java-platform` plugin
- **Template modules**: YAML-based pipeline definitions

## Migration from Mongock

The project includes comprehensive migration support:
- Legacy annotation compatibility maintained
- Importer tooling in `flamingock-importer` module
- Backward compatibility for existing Mongock installations

---
# Always, before answering, evaluate the request based on the following aspects, from 1-10:
- Clarity of the objective
- Context detail
- Potential of the result

If any of those aspects scores less than 8, please ask anything you need to achieve at least 8 in all the aspects.