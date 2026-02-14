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

## Terminology Guidelines

When writing code, documentation, or user-facing content for Flamingock:

### DO NOT use "migration" or "migrations"
- Flamingock is about **external system evolution**, not just database migrations
- Use "**changes**" (for individual units) or "**system evolution**" (for the concept)
- Example: "Apply pending changes" NOT "Apply pending migrations"

### CLI Naming
- The `cli/flamingock-cli-executor` module is THE Flamingock CLI (temporary internal name during transition)
- User-facing content should say "**Flamingock CLI**", not "CLI Executor"
- The old `cli/flamingock-cli` module is legacy and will be deprecated once feature parity is achieved

### Framework-Agnostic Messaging
- The CLI is designed to be framework-agnostic
- Currently only Spring Boot is supported, but this will expand
- User-facing CLI content should NOT mention "Spring Boot" - just say "applications"

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
- **Critical**: Hierarchical context MUST be built before AuditStore initialization

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
- `flamingock-core-api` - Public API annotations (`@Change`, `@Apply`)
- `flamingock-core-commons` - Shared internal utilities
- `flamingock-processor` - Annotation processor for pipeline generation
- `flamingock-graalvm` - GraalVM native image support

**Community Modules** (`community/`):
- Database-specific AuditStores (MongoDB, DynamoDB, Couchbase)
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
4. Hierarchical context MUST be complete before AuditStore initialization
5. AuditStore initialization provides auditPersistence for audit writer registration
6. Pipeline building contributes dependencies back to context

Violating this order will cause runtime failures due to missing dependencies during AuditStore initialization.

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

## Templates System (Deep Dive)

Templates are **reusable, declarative change definitions** that enable "no-code migrations". Instead of writing Java classes with `@Change` annotations, developers define changes in YAML files.

### Purpose and Motivation

1. **Reduce code duplication** - Common patterns (create tables, insert data) are standardized
2. **Enable non-developers** - Business analysts and DBAs can create migrations without Java
3. **Declarative over imperative** - YAML is more readable for well-defined operations
4. **GraalVM support** - Templates enable proper reflection registration at build time

### Core Architecture

**Interface**: `ChangeTemplate<SHARED_CONFIG, APPLY_FIELD, ROLLBACK_FIELD>`
- `SHARED_CONFIG` - Configuration shared between apply and rollback (use `Void` if not needed)
- `APPLY_FIELD` - Payload type for the apply operation
- `ROLLBACK_FIELD` - Payload type for the rollback operation

**Base Class**: `AbstractChangeTemplate` resolves generic types via reflection and provides:
- Field management: `changeId`, `isTransactional`, `configuration`
- Generic type resolution for APPLY and ROLLBACK payload classes
- Reflective class collection for GraalVM native image support

**Specialized Classes**:
- `AbstractSimpleTemplate`: For single-step changes with `setStep()`/`getStep()`
- `AbstractSteppableTemplate`: For multi-step changes with `setSteps()`/`getSteps()`

**Key Files**:
- `core/flamingock-core-api/src/main/java/io/flamingock/api/template/ChangeTemplate.java`
- `core/flamingock-core-api/src/main/java/io/flamingock/api/template/AbstractChangeTemplate.java`
- `core/flamingock-core-commons/src/main/java/io/flamingock/internal/common/core/template/ChangeTemplateManager.java`

### Existing Implementations

| Template | Location | CONFIG | APPLY | ROLLBACK |
|----------|----------|--------|-------|----------|
| `SqlTemplate` | `templates/flamingock-sql-template` | `Void` | `String` (raw SQL) | `String` (raw SQL) |
| `MongoChangeTemplate` | `templates/flamingock-mongodb-sync-template` | `Void` | `MongoOperation` | `MongoOperation` |

### YAML Structure

```yaml
id: create-users-table           # Unique identifier
author: developer-name           # Optional author
transactional: true              # Default: true
template: SqlTemplate            # Template class name (simple name)
targetSystem:
  id: "postgresql"               # Must match registered target system
apply: "CREATE TABLE users ..."  # Payload for apply (type depends on template)
rollback: "DROP TABLE users"     # Optional: payload for rollback
recovery:
  strategy: MANUAL_INTERVENTION  # Or ALWAYS_RETRY
```

### Execution Flow

```
YAML File
    ↓ (parsing)
ChangeTemplateFileContent
    ↓ (preview building)
TemplatePreviewChange (unified)
    ↓ (loaded task building - template lookup from registry)
AbstractTemplateLoadedChange
    ├── SimpleTemplateLoadedChange (for AbstractSimpleTemplate)
    └── SteppableTemplateLoadedChange (for AbstractSteppableTemplate)
    ↓ (execution preparation)
TemplateExecutableTask<T>
    ├── SimpleTemplateExecutableTask (calls setStep())
    └── SteppableTemplateExecutableTask (calls setSteps())
    ↓ (runtime execution)
Template instance with injected dependencies
```

**Key Classes in Flow**:
- `ChangeTemplateFileContent` - YAML parsed data (`core/flamingock-core-commons`)
- `TemplatePreviewTaskBuilder` - Builds preview from file content (`core/flamingock-core-commons`)
- `TemplateLoadedTaskBuilder` - Resolves template class, builds type-specific loaded change (`core/flamingock-core`)
- `TemplateExecutableTaskBuilder` - Builds type-specific executable task (`core/flamingock-core`)
- `TemplateExecutableTask<T>` - Abstract base for template execution (`core/flamingock-core`)

### Discovery Mechanism (SPI)

Templates are discovered via Java's `ServiceLoader`:

**Direct Registration**:
```
META-INF/services/io.flamingock.api.template.ChangeTemplate
→ io.flamingock.template.sql.SqlTemplate
```

**Factory Registration** (for multiple templates):
```
META-INF/services/io.flamingock.internal.common.core.template.ChangeTemplateFactory
→ com.example.MyTemplateFactory
```

**ChangeTemplateManager** loads all templates at startup and provides lookup by simple class name.

### Ordering

Change execution order is determined **solely by filename convention**:
- `_0001__create_users.yaml` runs before `_0002__seed_data.yaml`
- No explicit `order` field in YAML; order comes from filename prefix

### Transactionality and Rollback

- `transactional: true` is the **default**
- For ACID databases, Flamingock manages rollback automatically via native DB transactions
- Manual rollback is **optional but recommended** because:
  - Required for non-transactional operations (e.g., MongoDB DDL)
  - Used by CLI `UNDO` operation to revert already-committed changes

### Recovery Strategy

When a change fails and cannot be rolled back:

| Strategy | Behavior |
|----------|----------|
| `MANUAL_INTERVENTION` (default) | Requires user intervention before retry |
| `ALWAYS_RETRY` | Safe to retry automatically (for idempotent operations) |

**File**: `core/flamingock-core-api/src/main/java/io/flamingock/api/RecoveryStrategy.java`

### Compile-Time Template Validation

Templates are validated at compile-time to ensure YAML structure matches the template type:

**SimpleTemplate** (`AbstractSimpleTemplate`):
- MUST have `apply` field
- MAY have `rollback` field
- MUST NOT have `steps` field

**SteppableTemplate** (`AbstractSteppableTemplate`):
- MUST have `steps` field
- MUST NOT have `apply` or `rollback` fields at root level
- Each step MUST have `apply` field

**Configuration:**
```java
@EnableFlamingock(
    configFile = "pipeline.yaml",
    strictTemplateValidation = true  // default
)
```

| Flag Value | Behavior |
|------------|----------|
| `true` (default) | Compilation fails with detailed error |
| `false` | Warning logged, compilation continues |

**Validation Location:** `TemplateValidator` in `core/flamingock-core-commons/.../template/`

**Key Files:**
- `io.flamingock.internal.common.core.template.TemplateValidator` - validation logic
- `io.flamingock.api.annotations.EnableFlamingock` - strictTemplateValidation flag
- `io.flamingock.api.template.AbstractChangeTemplate` - template base classes

<<<<<<< Updated upstream
=======
### Template Class Hierarchy (Loaded & Executable)

At the **Loaded** and **Executable** phases, templates are split into type-specific classes:

**Loaded Phase:**
```
AbstractTemplateLoadedChange (abstract base)
├── SimpleTemplateLoadedChange (apply, rollback)
└── SteppableTemplateLoadedChange (steps)
```

**Executable Phase:**
```
TemplateExecutableTask<T> (abstract base)
├── SimpleTemplateExecutableTask (calls setStep())
└── SteppableTemplateExecutableTask (calls setSteps())
```

**Type Detection:** Happens in `TemplateLoadedTaskBuilder.build()` using:
- `AbstractSteppableTemplate.class.isAssignableFrom(templateClass)` → SteppableTemplateLoadedChange
- Otherwise → SimpleTemplateLoadedChange (default for AbstractSimpleTemplate and unknown types)

**Note:** Preview phase (`TemplatePreviewChange`) remains unified since YAML is parsed before template type is known.

### SteppableTemplateExecutableTask Apply/Rollback Lifecycle

The `SteppableTemplateExecutableTask` manages multi-step execution with per-step rollback:

**Apply Phase:**
- Iterates through steps in order (0 → N-1)
- Sets `applyPayload` on template instance before executing `@Apply` method
- `stepIndex` tracks current progress (-1 before start, N-1 after all steps complete)
- On failure at step N, `stepIndex = N` (points to failed step)

**Rollback Phase (on apply failure):**
- Rolls back from current `stepIndex` down to 0 (reverse order)
- Sets `rollbackPayload` on template instance before executing `@Rollback` method
- **Skips steps without rollback payload** (`hasRollback()` returns false)
- **Skips if template has no `@Rollback` method** (logs warning)

**Key Design Decision:** Same `SteppableTemplateExecutableTask` instance is used for both apply and rollback (no retry). The `stepIndex` state persists to enable rollback from the exact failure point.

### Dependency Injection in Templates

Template methods (`@Apply`, `@Rollback`) receive dependencies as **method parameters**, not constructor injection:

```java
@Apply
public void apply(Connection connection) {  // Injected from context
    execute(connection, applyPayload);
}
```

Dependencies are resolved from the `ContextResolver` based on type matching.

### Creating Custom Templates

1. Extend `AbstractChangeTemplate<CONFIG, APPLY, ROLLBACK>`
2. Implement `@Apply` method (required)
3. Implement `@Rollback` method (optional but recommended)
4. Register in `META-INF/services/io.flamingock.api.template.ChangeTemplate`

**Documentation**: https://docs.flamingock.io/templates/create-your-own-template

### GraalVM Support

Templates must declare reflective classes for native image compilation:
- Override `getReflectiveClasses()` in template
- Pass additional classes to `AbstractChangeTemplate` constructor
- `RegistrationFeature` in `flamingock-graalvm` module handles registration

### Evolution Proposals

See `docs/TEMPLATES_EVOLUTION_PROPOSALS.md` for comprehensive proposals including:
- Variables and interpolation
- Dry-run/Plan mode
- JSON Schema validation
- Explicit dependencies (`depends_on`)
- Policy as Code
- And more...