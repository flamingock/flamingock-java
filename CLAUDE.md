# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

🔹 **What Flamingock IS**

Flamingock is a platform for the audited, synchronized evolution of distributed systems.

It enables Change-as-Code (CaC): all changes to external systems (schemas, configs, storage, infra-adjacent systems, etc.) are written as versioned, executable, auditable units of code.

It applies changes safely, in lockstep with the application lifecycle, not through CI/CD pipelines.

It provides a Client Library (open-source, Community Edition) and a Cloud Backend (SaaS or Self-Hosted) for governance, visibility, and advanced features.

It works across databases (MongoDB, DynamoDB, SQL, etc.), event schemas (Kafka + Schema Registry, Avro, Protobuf), configs, S3 buckets, queues, and more.

It ensures auditability, safety, synchronization, governance, and visibility across all system evolutions.

🔹 **What Flamingock is NOT**

It is not a database migration tool tied to a single DB (like Mongock, Flyway, or Liquibase).

It is not a CI/CD pipeline or a replacement for tools like GitHub Actions, Jenkins, or ArgoCD.

It is not an infra-as-code tool like Terraform or Pulumi (though it's conceptually close in ambition, but focused on system evolution instead of infra provisioning).

It is not limited to databases — databases are only one type of target system.

🔹 **Goals of Flamingock**

Unify external system evolution under a single, auditable, code-driven model.

Ensure safety and resilience with strong execution guarantees (idempotency, manual intervention, and safe retry in Cloud).

Provide governance & compliance via audit logs, approvals, visibility, and policy controls.

Boost developer productivity by making changes versioned, testable, and executable in sync with the app lifecycle.

Enable organizational coordination for distributed teams and services evolving multiple systems in parallel.

🔹 **Ambitions & Vision**

Become the standard for controlled, auditable, and intelligent system evolution, in the same way Terraform became the standard for infrastructure.

Extend Change-as-Code (CaC) to all external dependencies of an application (schemas, configs, storages, event systems, etc.).

Provide a cloud-native platform (Cloud Edition) with governance, dashboards, approvals, observability, and AI-assisted evolution planning.

Build an open-core business model:
- Community Edition → OSS, self-contained, no backend.
- Cloud Edition → SaaS, premium automation and governance features.
- Self-Hosted Edition → same as Cloud, but deployable on customer infra.

**👉 North Star:** Flamingock = Change-as-Code platform for audited, synchronized evolution of distributed systems. Not just DB migrations. Not CI/CD. Not infra-as-code. Its ambition = Terraform-equivalent for system evolution.

## Build System

This is a multi-module Gradle project using Kotlin DSL.

### Common Commands

```bash
# Build entire project
./gradlew build

# Run tests for entire project
./gradlew test

# Run tests for specific module
./gradlew :core:flamingock-core:test

# Build and publish locally
./gradlew publishToMavenLocal

# Clean build
./gradlew clean build

# Run tests with debugging info
./gradlew test --info

# Build specific module only
./gradlew :core:flamingock-core:build
```

### Release Commands

```bash
# Release specific module
./gradlew -Pmodule=flamingock-core jreleaserFullRelease

# Release entire bundle
./gradlew -PreleaseBundle=core jreleaserFullRelease
./gradlew -PreleaseBundle=community jreleaserFullRelease
./gradlew -PreleaseBundle=cloud jreleaserFullRelease
```

## Architecture Overview

### Core Components

**Flamingock Builder Pattern**: Central configuration through `AbstractFlamingockBuilder` with hierarchical builder inheritance:
- `CommunityFlamingockBuilder` - Community Edition
- `CloudFlamingockBuilder` - Cloud Edition  
- `AbstractFlamingockBuilder.build()` method orchestrates component assembly with critical ordering dependencies

**Context System**: Hierarchical dependency injection via `Context` and `ContextResolver`:
- Base context contains runner ID and core configuration
- Plugin contexts merged via `PriorityContextResolver`
- External frameworks (Spring Boot) contribute dependency contexts
- **Critical**: Hierarchical context MUST be built before driver initialization

**Pipeline Architecture**: Change execution organized in stages:
- `LoadedPipeline` - Executable pipeline with stages and changes
- `pipeline.yaml` - Declarative pipeline definition in `src/test/resources/flamingock/`
- Stages contain changes which are atomic migration operations

**AuditStore System**: Database/system-specific implementations:
- `AuditStore` interface provides `ConnectionEngine` for specific technologies
- AuditStores live in community modules (e.g., `flamingock-auditstore-mongodb-sync`, `flamingock-auditstore-dynamodb`)
- AuditStore initialization requires full hierarchical context for dependency resolution

**Plugin System**: Extensible architecture via `Plugin` interface:
- Contribute task filters, event publishers, and dependency contexts
- Platform plugins (e.g., `flamingock-springboot-integration`) provide framework integration
- Initialized after base context setup but before hierarchical context building

### Module Organization

**Core Modules** (`core/`):
- `flamingock-core` - Core engine and orchestration logic
- `flamingock-core-api` - Public API annotations (`@Change`, `@Execution`)
- `flamingock-core-commons` - Shared internal utilities
- `flamingock-processor` - Annotation processor for pipeline generation
- `flamingock-graalvm` - GraalVM native image support

**Community Modules** (`community/`):
- Database-specific drivers (MongoDB, DynamoDB, Couchbase)
- `flamingock-importer` - Import from legacy systems (Mongock)
- Version-specific implementations (e.g., Spring Data v3 legacy)

**Platform Plugins** (`platform-plugins/`):
- `flamingock-springboot-integration` - Spring Boot auto-configuration
- Event publishers for Spring application events

**Templates** (`templates/`):
- `flamingock-sql-template` - Template for SQL-based changes
- `flamingock-mongodb-sync-template` - Template for MongoDB changes
- Templates enable YAML-based change definitions

**Transactioners** (`transactioners/`):
- Cloud-specific transaction handling
- Database-specific transaction wrappers

### Key Patterns

**Changes**: Atomic migration operations annotated with `@Change`:
- `id` - Unique identifier for tracking
- `order` - Execution sequence (can be auto-generated)
- `author` - Change author
- `transactional` - Whether to run in transaction

**Template System**: No-code migrations via `ChangeTemplate`:
- YAML pipeline definitions processed by templates
- Templates registered via SPI in `META-INF/services/`
- Enable non-developers to create migrations

**Event System**: Observable pipeline execution:
- Pipeline events: Started, Completed, Failed, Ignored
- Stage events: Started, Completed, Failed, Ignored
- Plugin-contributed event publishers for framework integration

## Development Guidelines

### Module Dependencies
- Core modules form the foundation - avoid circular dependencies
- Community modules depend on core but not each other
- Platform plugins integrate core with external frameworks
- Templates provide declarative change authoring

### Testing Approach
- Uses JUnit 5 (`org.junit.jupiter:junit-jupiter-api:5.9.2`)
- Mockito for mocking (`org.mockito:mockito-core:4.11.0`)
- Test resources in `src/test/resources/flamingock/pipeline.yaml`
- Each module has isolated test suite

### Java Version
- Target Java 8 compatibility
- Kotlin stdlib used in build scripts only
- GraalVM native image support via `flamingock-graalvm` module

### Package Structure
- Public API: `io.flamingock.api.*`
- Internal core: `io.flamingock.internal.core.*`
- Community features: `io.flamingock.community.*`
- Cloud features: `io.flamingock.cloud.*`
- Templates: `io.flamingock.template.*`

### Critical Build Order Dependencies
When modifying the builder pattern in `AbstractFlamingockBuilder.build()`:
1. Template loading must occur first
2. Base context preparation before plugin initialization  
3. Plugin initialization before hierarchical context building
4. Hierarchical context MUST be complete before driver initialization
5. AuditStore initialization provides auditPersistence for audit writer registration
6. Pipeline building contributes dependencies back to context

Violating this order will cause runtime failures due to missing dependencies during driver initialization.

## License Header Management

All Java and Kotlin source files must include the Flamingock license header:

### Automatic Header Addition
- **IntelliJ IDEA**: File templates in `.idea/fileTemplates/` automatically add headers to new files
- **Other IDEs**: Manual header addition required (see template in any existing source file)

### Gradle Commands
```bash
# Check if all files have proper license headers
./gradlew spotlessCheck

# Automatically add missing license headers
./gradlew spotlessApply

# Normal build (does NOT check headers - keeps builds fast)
./gradlew build
```

### License Header Format
```java
/*
 * Copyright YYYY Flamingock (https://www.flamingock.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
```

**Note**: YYYY should be the current year for new files. Existing files retain their original copyright year.

### GitHub Actions Enforcement
- PR-based license header validation runs automatically
- Can be added as required status check in branch protection rules
- Provides clear instructions for fixing header issues

## Execution Flow Architecture

**📖 Complete Documentation**: See `docs/EXECUTION_FLOW_GUIDE.md` for comprehensive execution flow from builder through pipeline completion, including StageExecutor, ExecutionPlanner, StepNavigator, transaction handling, and rollback mechanisms.