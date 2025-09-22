# Flamingock Developer Operations Guide
**Document Version**: 1.0  
**Date**: 2025-01-31  
**Authors**: Antonio Perez Dieppa  
**Audience**: New Developers, Architecture Team  

## Overview
A comprehensive guide covering the complete developer lifecycle for the Flamingock project - from local development to production release.

## 🚀 Quick Reference

### 🔧 Development Operations
| Operation         | Command                         | Use When                              |
|-------------------|---------------------------------|---------------------------------------|
| **Full Build**    | `./gradlew build`               | Before commits, complete verification |
| **Quick Compile** | `./gradlew compileJava`         | Syntax/compilation check              |
| **Run Tests**     | `./gradlew test`                | Verifying code changes                |
| **Local Publish** | `./gradlew publishToMavenLocal` | Testing in other projects             |
| **Clean Build**   | `./gradlew clean build`         | Fresh start, troubleshooting          |

### 📤 Submission Operations
| Operation                 | Command                                            | Use When                 |
|---------------------------|----------------------------------------------------|--------------------------|
| **Fix License Headers**   | `./gradlew spotlessApply`                          | Before committing        |
| **Check License Headers** | `./gradlew spotlessCheck`                          | Verify compliance        |
| **Squash Commits**        | `./infra/squash-against-master.sh "feat: message"` | Preparing feature branch |
| **Validate Build**        | `./gradlew clean build spotlessCheck`              | Pre-push verification    |

### 🚀 Production Operations
| Operation               | Command                                              | Use When                 |
|-------------------------|------------------------------------------------------|--------------------------|
| **Publish All Remote**  | `./gradlew publish -PreleaseBundle=all`              | Deploy to repositories   |
| **Publish Core Bundle** | `./gradlew publish -PreleaseBundle=core`             | Core modules only        |
| **Full Release**        | `./gradlew jreleaserFullRelease -PreleaseBundle=all` | Complete release process |
| **GitHub Release**      | `./gradlew jreleaserRelease`                         | Create GitHub release    |
| **Release with Retry**  | `./infra/bundle-release-with-retry.sh all`           | Automated retry logic    |

### 🛠️ Infrastructure Scripts
| Script                         | Purpose                                            | Used By        |
|--------------------------------|----------------------------------------------------|----------------|
| `squash-against-master.sh`     | Squash commits with conventional commit validation | **Developers** |
| `bundle-release-with-retry.sh` | Release bundles with retry logic                   | **CI/CD**      |
| `module-release-with-retry.sh` | Release individual modules with retry              | **CI/CD**      |

---

## 🔄 Developer Lifecycle

### 🔧 Phase 1: Development

This phase covers your day-to-day development activities on your local machine.

#### Local Development

**Quick Development Cycle**
```bash
# Fast compilation check
./gradlew compileJava

# Run specific tests
./gradlew :core:flamingock-core:test

# Full verification
./gradlew build
```

**Working with Specific Modules**
```bash
# Build specific module
./gradlew :core:flamingock-core:build

# Test specific module  
./gradlew :community:flamingock-auditstore-mongodb-sync:test

# Publish specific module locally
./gradlew :core:flamingock-core:publishToMavenLocal
```

#### Local Testing & Integration

**Publish to Local Maven** for testing in other projects:
```bash
# Publish all modules locally
./gradlew publishToMavenLocal

# Publish specific bundle locally
./gradlew publishToMavenLocal -PreleaseBundle=core
```
- **Output**: Artifacts in `~/.m2/repository/io/flamingock/`
- **Use case**: Testing Flamingock changes in dependent projects

**Continuous Development**
```bash
# Watch for changes and re-run tests
./gradlew test --continuous

# Clean slate when things get messy
./gradlew clean build
```

#### Code Quality Checks

**License Header Management**
```bash
# Check license headers (fast)
./gradlew spotlessCheck

# Fix license headers automatically
./gradlew spotlessApply
```
> 💡 **Tip**: Run `spotlessApply` before every commit to avoid CI failures

**Development Best Practices**
- Run `./gradlew compileJava` frequently for quick feedback
- Use `./gradlew build` before important commits
- Keep license headers up to date with `spotlessApply`

---

### 📤 Phase 2: Submission

This phase covers preparing and submitting your changes.

#### Pre-Commit Preparation

**Essential Pre-Commit Checklist**
```bash
# 1. Fix any license header issues
./gradlew spotlessApply

# 2. Full build with tests
./gradlew clean build

# 3. Verify license compliance
./gradlew spotlessCheck
```

#### Commit Preparation

**Using the Squash Script** (Recommended)
```bash
# Squash all commits in your feature branch with conventional commit message
./infra/squash-against-master.sh "feat(core): add new execution engine"
./infra/squash-against-master.sh "fix(mongodb): resolve connection timeout issue"
./infra/squash-against-master.sh "docs: update API documentation"
```

**What the squash script does:**
- ✅ Validates conventional commit format
- ✅ Squashes all commits since branching from master
- ✅ Creates single commit with your message
- ✅ Provides clear next steps

**Conventional Commit Format:**
```
type(scope): description

Examples:
feat(core): add new pipeline executor
fix(mongodb): resolve transaction timeout
docs: update getting started guide
chore: bump dependency versions
```

**Valid types**: `feat`, `fix`, `chore`, `docs`, `style`, `refactor`, `perf`, `test`, `build`, `ci`, `revert`

#### Push Process

**After squashing, push your changes:**
```bash
git push --force
```

**What to expect:**
- GitHub Actions will automatically trigger
- CI will run build, tests, and validations
- You'll see status checks on your PR

---

### 🤖 Phase 3: CI/CD (GitHub Actions)

This phase covers what happens automatically when you push changes or create PRs.

#### Pull Request Validation

**Build Workflow** (`build.yml`)
- **Triggers**: Every pull request
- **What it does**: 
  - Checks out code
  - Sets up GraalVM Java 17
  - Runs `./gradlew clean build`
- **Expected duration**: ~3-5 minutes
- **Success criteria**: All tests pass, compilation succeeds

**License Check Workflow** (`license-check.yml`)
- **Triggers**: PRs and pushes to master/main
- **What it does**:
  - Sets up JDK 8
  - Runs `./gradlew spotlessCheck`
- **Expected duration**: ~30 seconds
- **Success criteria**: All files have proper license headers

**Commit Validation Workflow** (`validate-commits.yml`)
- **Triggers**: PRs and pushes to develop/master
- **What it does**:
  - Validates last commit follows [Conventional Commits](https://conventionalcommits.org/)
  - Checks commit message format
- **Expected duration**: ~15 seconds
- **Success criteria**: Commit message follows conventional format

#### Understanding CI Results

**✅ All Checks Pass**
- Your PR is ready for review
- Code builds successfully
- All tests pass
- License headers are correct
- Commit message follows conventions

**❌ Check Failures**

*Build failure:*
```bash
# Run locally to reproduce
./gradlew clean build
```

*License check failure:*
```bash
# Fix automatically
./gradlew spotlessApply
git add -A && git commit -m "fix: apply license headers"
git push
```

*Commit validation failure:*
- Use conventional commit format
- Re-squash with proper message: `./infra/squash-against-master.sh "feat: proper message"`

#### What Reviewers See

When your PR is ready:
- All automated checks are green ✅
- Clear, single commit with conventional message
- Code follows project standards
- Changes are focused and well-tested

---

### 🚀 Phase 4: Production

This phase covers release operations for maintainers and the release process.

#### Release Bundles

Flamingock supports releasing different bundles of modules:

**Available Bundles:**
- `core` - Core engine modules
- `cloud` - Cloud edition modules  
- `community` - Community edition modules
- `plugins` - Platform integration plugins
- `transactioners` - Transaction handlers
- `templates` - Change templates
- `utils` - Utility modules
- `all` - Everything

#### Local Release Testing

**Test Release Process Locally**
```bash
# Publish specific bundle to local Maven
./gradlew publishToMavenLocal -PreleaseBundle=core

# Publish everything to local Maven
./gradlew publishToMavenLocal -PreleaseBundle=all
```

#### Production Release Operations

**Remote Publishing** (Requires authentication)
```bash
# Publish core modules to remote repositories
./gradlew publish -PreleaseBundle=core

# Publish all modules to remote repositories  
./gradlew publish -PreleaseBundle=all

# Publish specific module
./gradlew publish -Pmodule=flamingock-core
```

**Full Release Process**
```bash
# Complete release: publish artifacts + create GitHub release + announcements
./gradlew jreleaserFullRelease -PreleaseBundle=all

# Deploy to Maven Central only (no GitHub release)
./gradlew jreleaserDeploy -PreleaseBundle=core

# Create GitHub release only (no artifact deployment)
./gradlew jreleaserRelease
```

#### Production Infrastructure Scripts

**Automated Release with Retry Logic** (Used in CI/CD)
```bash
# Release bundle with automatic retries
./infra/bundle-release-with-retry.sh all 3 20
# Arguments: bundle, max-attempts, wait-seconds

# Release specific module with retries
./infra/module-release-with-retry.sh flamingock-core 3 20
```

#### GitHub Actions Release Workflows

**Release Workflows:**
- `github-release.yml` - Full production release process
- `module-release-*.yml` - Module-specific releases for different JVM versions

**Production Release Trigger:**
- Manual workflow dispatch
- Automated on version tags
- Uses infrastructure scripts with retry logic

**What happens in production release:**
1. **Build & Test**: Full build with all tests
2. **Sign Artifacts**: GPG signing for Maven Central
3. **Deploy**: Push to Maven Central via Sonatype
4. **GitHub Release**: Create release with generated changelog
5. **Announcements**: Notify release channels

---

## 🔍 Advanced Operations

### Module-Specific Development

**Core Modules:**
```bash
./gradlew :core:flamingock-core:build
./gradlew :core:flamingock-core-api:publishToMavenLocal
```

**Community AuditStores:**
```bash
./gradlew :community:flamingock-auditstore-mongodb-sync:test
./gradlew :community:flamingock-auditstore-dynamodb:build
```

**Platform Plugins:**
```bash
./gradlew :platform-plugins:flamingock-springboot-integration:test
```

### Performance & Debugging

**Fast Development Builds:**
```bash
# Skip tests for faster builds
./gradlew build -x test

# Use build cache
./gradlew build --build-cache

# Parallel execution
./gradlew build --parallel
```

**Debugging:**
```bash
# Verbose test output
./gradlew test --info

# Debug dependency issues
./gradlew dependencies
./gradlew dependencyInsight --dependency slf4j-api

# Clean everything and rebuild
./gradlew clean build --no-daemon
```

### Release Management

**Changelog Generation:**
```bash
# Generate changelog for upcoming release
./gradlew jreleaserChangelog
```

**Release Configuration:**
```bash
# View current release configuration
./gradlew jreleaserConfig

# Validate release setup
./gradlew jreleaserRelease --dry-run
```

---

## 🚨 Troubleshooting

### Common Development Issues

**Build Failures**
```bash
# Clean and rebuild
./gradlew clean build

# Check for dependency conflicts
./gradlew dependencies --scan
```

**Test Failures**
```bash
# Run tests with detailed output
./gradlew test --info

# Clean test state and re-run
./gradlew cleanTest test

# Run specific test class
./gradlew test --tests "SpecificTestClass"
```

**License Header Issues**
```bash
# See which files have issues
./gradlew spotlessCheck

# Fix automatically
./gradlew spotlessApply
```

### CI/CD Issues

**GitHub Actions Failing**

*Build workflow failure:*
- Check if build passes locally: `./gradlew clean build`
- Review error logs in GitHub Actions tab
- Ensure all dependencies are available

*License check failure:*
- Run `./gradlew spotlessApply` and commit the changes
- Push to update the PR

*Commit validation failure:*
- Use conventional commit format
- Re-squash commits: `./infra/squash-against-master.sh "proper message"`

### Production Issues

**Release Failures**
- Check authentication credentials are set
- Verify version hasn't already been released
- Use retry scripts: `./infra/bundle-release-with-retry.sh`

**Maven Central Issues**
- Releases can take up to 4 hours to appear in search
- Check [Maven Central Repository](https://repo1.maven.org/maven2/io/flamingock/)
- Verify GPG signing is working

---

## 📚 Related Resources

- **[CLAUDE.md](../CLAUDE.md)** - Development setup and architecture guide
- **[Build Configuration](../build.gradle.kts)** - Main build script
- **[Convention Plugins](./buildSrc/)** - Build logic and plugins
- **[Version Catalog](../gradle/libs.versions.toml)** - Dependency management
- **[GitHub Actions](./.github/workflows/)** - CI/CD workflows
- **[Infrastructure Scripts](./infra/)** - Release and utility scripts

## 🎯 Quick Start Workflows

### New Developer Setup
```bash
# 1. Clone and build
git clone <repo-url>
cd flamingock-java
./gradlew build

# 2. Verify everything works
./gradlew publishToMavenLocal
```

### Feature Development
```bash
# 1. Create feature branch
git checkout -b feature/my-feature

# 2. Develop with quick feedback
./gradlew compileJava  # frequent checks
./gradlew test         # after changes

# 3. Pre-commit verification
./gradlew spotlessApply
./gradlew clean build

# 4. Prepare for submission
./infra/squash-against-master.sh "feat(core): add new feature"
git push --force
```

### Release Preparation (Maintainers)
```bash
# 1. Validate release
./gradlew clean build spotlessCheck

# 2. Test release locally
./gradlew publishToMavenLocal -PreleaseBundle=all

# 3. Execute production release
./gradlew jreleaserFullRelease -PreleaseBundle=all
```

---

*This guide covers the complete Flamingock development lifecycle. For specific technical details, refer to the linked resources above.*