# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Flamingock is an open-source Change-as-Code engine for auditable, versioned changes across distributed systems. It enables synchronized evolution of databases, queues, APIs, configurations, and resources during application startup with governance, auditability, and rollback capabilities.

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
- `LoadedPipeline` - Executable pipeline with stages and change units
- `pipeline.yaml` - Declarative pipeline definition in `src/test/resources/flamingock/`
- Stages contain change units which are atomic migration operations

**Driver System**: Database/system-specific implementations:
- `Driver` interface provides `ConnectionEngine` for specific technologies
- Drivers live in community modules (e.g., `flamingock-ce-mongodb-sync`, `flamingock-ce-dynamodb`)
- Driver initialization requires full hierarchical context for dependency resolution

**Plugin System**: Extensible architecture via `Plugin` interface:
- Contribute task filters, event publishers, and dependency contexts
- Platform plugins (e.g., `flamingock-springboot-integration`) provide framework integration
- Initialized after base context setup but before hierarchical context building

### Module Organization

**Core Modules** (`core/`):
- `flamingock-core` - Core engine and orchestration logic
- `flamingock-core-api` - Public API annotations (`@ChangeUnit`, `@Execution`)
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

**Change Units**: Atomic migration operations annotated with `@ChangeUnit`:
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
5. Driver initialization provides engine for audit writer registration
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