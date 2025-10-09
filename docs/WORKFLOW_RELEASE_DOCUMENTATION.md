# Comprehensive Documentation of Flamingock Release Workflow

## Table of Contents
1. [Overview](#overview)
2. [Main Workflow Structure](#main-workflow-structure)
3. [Module Release Workflow](#module-release-workflow)
4. [GitHub Release Workflow](#github-release-workflow)
5. [Useful Commands for Local Development](#useful-commands-for-local-development)
6. [jReleaser Configuration](#jreleaser-configuration)
7. [Detailed jReleaser Steps](#detailed-jreleaser-steps)
8. [Local Testing Commands](#local-testing-commands)
9. [Infrastructure Scripts](#infrastructure-scripts)

---

## Overview

The Flamingock release system is designed to publish 25 different modules to Maven Central and create releases on GitHub. It uses jReleaser v1.15.0 as the main tool to manage the publication process.

### Modules to Publish

**Core (5 modules):**
- flamingock-core
- flamingock-core-commons
- flamingock-core-api
- flamingock-processor
- flamingock-graalvm

**Cloud (2 modules):**
- flamingock-cloud
- flamingock-cloud-bom

**Community (6 modules):**
- flamingock-community
- flamingock-community-bom
- flamingock-auditstore-mongodb-sync
- flamingock-auditstore-couchbase
- flamingock-auditstore-dynamodb
- flamingock-importer

**Plugins (1 module):**
- flamingock-springboot-integration

**Target Systems (6 modules):**
- nontransactional-target-system
- mongodb-sync-target-system
- mongodb-springdata-target-system
- sql-target-system
- dynamodb-target-system
- couchbase-target-system

**Templates (2 modules, but not published for now):**
- flamingock-sql-template
- flamingock-mongodb-sync-template

**Utils (5 modules):**
- general-util
- test-util
- mongodb-util
- dynamodb-util
- couchbase-util

---

## Main Workflow Structure

### File: `.github/workflows/release.yml`

The main workflow is structured in several jobs that run in sequence:

### 1. Job: `build`
**Purpose:** Compilation and initial testing of the complete project

**Local equivalent command:**
```bash
./gradlew clean build
```

### 2. Jobs: Module Release (25 jobs in parallel)

Each of the 25 modules is published individually using the reusable workflow `.github/workflows/module-release-graalvm.yml`

**Dependencies:**
- Waits for the `build` job to finish successfully

### 3. Job: `github-release`

**Dependencies:**
- Waits for all 25 modules to be successfully published to Maven Central

**Purpose:**
- Create the release on GitHub with CLI artifacts

---

## Module Release Workflow

### File: `.github/workflows/module-release-graalvm.yml`

This workflow publishes an individual module to Maven Central.

### Step 1: Prepare local publication (staging)

**Local command:**
```bash
./gradlew publish -Pmodule={module-name}
```

**Required environment variables:**
```bash
export JRELEASER_MAVENCENTRAL_USERNAME="your_username"
export JRELEASER_MAVENCENTRAL_PASSWORD="your_token"
```

**What it does:**
- Compiles the module
- Generates JARs (main, sources, javadoc)
- Creates the POM file with complete metadata
- Publishes to a local staging repository (`build/staging-deploy`)

### Step 2: Deploy to Maven Central with retries

**Local command:**
```bash
./infra/module-release-with-retry.sh {module-name} 5 20
```

**Required environment variables:**
```bash
export JRELEASER_GITHUB_TOKEN="ghp_..."
export JRELEASER_MAVENCENTRAL_USERNAME="your_username"
export JRELEASER_MAVENCENTRAL_PASSWORD="your_token"
export JRELEASER_GPG_PUBLIC_KEY="-----BEGIN PGP PUBLIC KEY BLOCK-----..."
export JRELEASER_GPG_SECRET_KEY="-----BEGIN PGP PRIVATE KEY BLOCK-----..."
export JRELEASER_GPG_PASSPHRASE="your_passphrase"
```

**Parameters:**
- Module name
- 5 maximum attempts
- 20 seconds wait between retries

**What it does:**
- Executes `./gradlew jreleaserDeploy -Pmodule={module}` with retry logic
- Signs artifacts with GPG
- Generates checksums
- Validates artifacts against Maven Central rules
- Uploads bundle to Maven Central
- Maven Central validates and publishes automatically

---

## GitHub Release Workflow

### File: `.github/workflows/github-release.yml`

**Local equivalent command:**
```bash
./gradlew jreleaserRelease --stacktrace
```

**Required environment variables:**
```bash
export JRELEASER_GITHUB_TOKEN="ghp_..."
export JRELEASER_GPG_PUBLIC_KEY="-----BEGIN PGP PUBLIC KEY BLOCK-----..."
export JRELEASER_GPG_SECRET_KEY="-----BEGIN PGP PRIVATE KEY BLOCK-----..."
export JRELEASER_GPG_PASSPHRASE="your_passphrase"
```

**What it does:**
- Creates a release on GitHub
- Automatically generates the changelog (Conventional Commits)
- Uploads CLI artifacts (zip, tar.gz, checksums)

**Internal process:**

1. **Compile CLI artifacts:**
   ```bash
   ./gradlew :cli:flamingock-cli:assemble
   ```

   Generates the following files in `cli/flamingock-cli/build/distributions/`:
   - `flamingock-cli.zip`
   - `flamingock-cli.tar.gz`
   - `checksums.txt`

2. **Copy artifacts to jReleaser directory:**

   The `copyReleaseFiles` task (automatically executed as a dependency of `jreleaserRelease`) copies files from:
   - **Source:** `cli/flamingock-cli/build/distributions/`
   - **Destination:** `build/jreleaser/distributions/`

3. **Create release on GitHub:**

   jReleaser uses files from the `build/jreleaser/distributions/` directory to:
   - Generate changelog by analyzing commits
   - Create/update the release on GitHub
   - Upload the 3 artifacts as release assets

---

## Useful Commands for Local Development

This section includes additional jReleaser and project commands that are useful during development and testing of the release process.

### 1. Generate CLI Artifacts

```bash
# Compile and extract distribution
./gradlew :cli:flamingock-cli:assemble
cd cli/flamingock-cli/build/distributions/
unzip flamingock-cli.zip
cd flamingock-cli/

# Run CLI
./bin/flamingock --help
./bin/flamingock --version
```

### 2. View jReleaser Configuration

```bash
# Show complete jReleaser configuration
./gradlew jreleaserConfig --stacktrace

# View required environment variables
./gradlew jreleaserEnv --stacktrace
```

### 3. Generate and View Changelog

```bash
# Generate changelog without publishing (dry-run)
./gradlew jreleaserChangelog --stacktrace

# Changelog is generated in: build/jreleaser/release/CHANGELOG.md
cat build/jreleaser/release/CHANGELOG.md
```

### 4. Validate Artifacts Before Deploy

```bash
# Verify artifact checksums
./gradlew jreleaserChecksum --stacktrace

# Validate GPG signatures
./gradlew jreleaserSign --stacktrace
```

### 5. Simulate Complete Release (Dry Run)

```bash
# Simulate the entire process without actually executing anything
./gradlew jreleaserRelease --dry-run --stacktrace

# See which files would be uploaded
./gradlew jreleaserCatalog --stacktrace
```

### 6. Individual vs Bundle Deploy

```bash
# Deploy a specific module
./gradlew jreleaserDeploy -Pmodule=flamingock-core --stacktrace

# Deploy a complete bundle (core, cloud, community, etc.)
./gradlew jreleaserFullRelease -PreleaseBundle=core --stacktrace

# View all available modules (from code)
# coreProjects, cloudProjects, communityProjects, pluginProjects,
# targetSystemProjects, templateProjects, utilProjects
```

### 7. Verify Publication Status

```bash
# List modules and their publication status
./gradlew publish -Pmodule=flamingock-core --dry-run

# System automatically verifies if already published on Maven Central
# by querying: https://central.sonatype.com/api/v1/publisher/published
```

### 8. Clean Release Artifacts

```bash
# Clean only jReleaser artifacts
rm -rf build/jreleaser/
rm -rf cli/flamingock-cli/build/distributions/

# Clean publication staging
rm -rf build/staging-deploy/
rm -rf */build/staging-deploy/

# Complete cleanup
./gradlew clean
```

### 9. Advanced jReleaser Tasks

```bash
# Assemble all distributions
./gradlew jreleaserAssemble --stacktrace

# Package distributions
./gradlew jreleaserPackage --stacktrace

# Publish without creating release
./gradlew jreleaserPublish --stacktrace

# Full release (release + publish + announce)
./gradlew jreleaserFullRelease --stacktrace

# Generate JSON configuration schema
./gradlew jreleaserJsonSchema --stacktrace
```

### 10. Verify Task Dependencies

```bash
# View dependency tree of a task
./gradlew jreleaserRelease --dry-run --stacktrace

# View all available jReleaser tasks
./gradlew tasks --group=jreleaser
```

### 11. Diagnostic Commands

```bash
# Verify all environment variables are configured
env | grep JRELEASER

# Check project version
./gradlew properties | grep version

# List all project modules
./gradlew projects | grep flamingock
```
